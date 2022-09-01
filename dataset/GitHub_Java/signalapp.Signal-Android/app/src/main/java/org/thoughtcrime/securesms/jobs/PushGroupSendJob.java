package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase.GroupReceiptInfo;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.messages.GroupSendUtil;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingGroupUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.RecipientAccessList;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Preview;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Quote;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContextV2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class PushGroupSendJob extends PushSendJob {

  public static final String KEY = "PushGroupSendJob";

  private static final String TAG = Log.tag(PushGroupSendJob.class);

  private static final String KEY_MESSAGE_ID       = "message_id";
  private static final String KEY_FILTER_RECIPIENT = "filter_recipient";

  private final long        messageId;
  private final RecipientId filterRecipient;

  public PushGroupSendJob(long messageId, @NonNull RecipientId destination, @Nullable RecipientId filterRecipient, boolean hasMedia) {
    this(new Job.Parameters.Builder()
                           .setQueue(destination.toQueueKey(hasMedia))
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         messageId, filterRecipient);

  }

  private PushGroupSendJob(@NonNull Job.Parameters parameters, long messageId, @Nullable RecipientId filterRecipient) {
    super(parameters);

    this.messageId       = messageId;
    this.filterRecipient = filterRecipient;
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context,
                             @NonNull JobManager jobManager,
                             long messageId,
                             @NonNull RecipientId destination,
                             @Nullable RecipientId filterAddress)
  {
    try {
      Recipient group = Recipient.resolved(destination);
      if (!group.isPushGroup()) {
        throw new AssertionError("Not a group!");
      }

      MessageDatabase      database            = DatabaseFactory.getMmsDatabase(context);
      OutgoingMediaMessage message             = database.getOutgoingMessage(messageId);
      Set<String>          attachmentUploadIds = enqueueCompressingAndUploadAttachmentsChains(jobManager, message);

      if (!DatabaseFactory.getGroupDatabase(context).isActive(group.requireGroupId()) && !isGv2UpdateMessage(message)) {
        throw new MmsException("Inactive group!");
      }

      jobManager.add(new PushGroupSendJob(messageId, destination, filterAddress, !attachmentUploadIds.isEmpty()), attachmentUploadIds, attachmentUploadIds.isEmpty() ? null : destination.toQueueKey());

    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putString(KEY_FILTER_RECIPIENT, filterRecipient != null ? filterRecipient.serialize() : null)
                             .build();
  }

  private static boolean isGv2UpdateMessage(@NonNull OutgoingMediaMessage message) {
    return (message instanceof OutgoingGroupUpdateMessage && ((OutgoingGroupUpdateMessage) message).isV2Group());
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    DatabaseFactory.getMmsDatabase(context).markAsSending(messageId);
  }

  @Override
  public void onPushSend()
      throws IOException, MmsException, NoSuchMessageException, RetryLaterException
  {
    MessageDatabase           database                   = DatabaseFactory.getMmsDatabase(context);
    OutgoingMediaMessage      message                    = database.getOutgoingMessage(messageId);
    long                      threadId                   = database.getMessageRecord(messageId).getThreadId();
    List<NetworkFailure>      existingNetworkFailures    = message.getNetworkFailures();
    List<IdentityKeyMismatch> existingIdentityMismatches = message.getIdentityKeyMismatches();

    ApplicationDependencies.getJobManager().cancelAllInQueue(TypingSendJob.getQueue(threadId));

    if (database.isSent(messageId)) {
      log(TAG, String.valueOf(message.getSentTimeMillis()),  "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    Recipient groupRecipient = message.getRecipient().fresh();

    if (!groupRecipient.isPushGroup()) {
      throw new MmsException("Message recipient isn't a group!");
    }

    if (groupRecipient.isPushV1Group()) {
      throw new MmsException("No GV1 messages can be sent anymore!");
    }

    try {
      log(TAG, String.valueOf(message.getSentTimeMillis()), "Sending message: " + messageId + ", Recipient: " + message.getRecipient().getId() + ", Thread: " + threadId + ", Attachments: " + buildAttachmentString(message.getAttachments()));

      if (!groupRecipient.resolve().isProfileSharing() && !database.isGroupQuitMessage(messageId)) {
        RecipientUtil.shareProfileIfFirstSecureMessage(context, groupRecipient);
      }

      List<Recipient> target;

      if      (filterRecipient != null)            target = Collections.singletonList(Recipient.resolved(filterRecipient));
      else if (!existingNetworkFailures.isEmpty()) target = Stream.of(existingNetworkFailures).map(nf -> Recipient.resolved(nf.getRecipientId(context))).toList();
      else                                         target = getGroupMessageRecipients(groupRecipient.requireGroupId(), messageId);

      RecipientAccessList accessList = new RecipientAccessList(target);

      List<SendMessageResult>   results = deliver(message, groupRecipient, target);
      Log.i(TAG, JobLogger.format(this, "Finished send."));

      List<NetworkFailure>             networkFailures           = Stream.of(results).filter(SendMessageResult::isNetworkFailure).map(result -> new NetworkFailure(accessList.requireIdByAddress(result.getAddress()))).toList();
      List<IdentityKeyMismatch>        identityMismatches        = Stream.of(results).filter(result -> result.getIdentityFailure() != null).map(result -> new IdentityKeyMismatch(accessList.requireIdByAddress(result.getAddress()), result.getIdentityFailure().getIdentityKey())).toList();
      ProofRequiredException           proofRequired             = Stream.of(results).filter(r -> r.getProofRequiredFailure() != null).findLast().map(SendMessageResult::getProofRequiredFailure).orElse(null);
      List<SendMessageResult>          successes                 = Stream.of(results).filter(result -> result.getSuccess() != null).toList();
      List<Pair<RecipientId, Boolean>> successUnidentifiedStatus = Stream.of(successes).map(result -> new Pair<>(accessList.requireIdByAddress(result.getAddress()), result.getSuccess().isUnidentified())).toList();
      Set<RecipientId>                 successIds                = Stream.of(successUnidentifiedStatus).map(Pair::first).collect(Collectors.toSet());
      List<NetworkFailure>             resolvedNetworkFailures   = Stream.of(existingNetworkFailures).filter(failure -> successIds.contains(failure.getRecipientId(context))).toList();
      List<IdentityKeyMismatch>        resolvedIdentityFailures  = Stream.of(existingIdentityMismatches).filter(failure -> successIds.contains(failure.getRecipientId(context))).toList();
      List<Recipient>                  unregisteredRecipients    = Stream.of(results).filter(SendMessageResult::isUnregisteredFailure).map(result -> Recipient.externalPush(context, result.getAddress())).toList();

      RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
      for (Recipient unregistered : unregisteredRecipients) {
        recipientDatabase.markUnregistered(unregistered.getId());
      }

      for (NetworkFailure resolvedFailure : resolvedNetworkFailures) {
        database.removeFailure(messageId, resolvedFailure);
        existingNetworkFailures.remove(resolvedFailure);
      }

      for (IdentityKeyMismatch resolvedIdentity : resolvedIdentityFailures) {
        database.removeMismatchedIdentity(messageId, resolvedIdentity.getRecipientId(context), resolvedIdentity.getIdentityKey());
        existingIdentityMismatches.remove(resolvedIdentity);
      }

      if (!networkFailures.isEmpty()) {
        database.addFailures(messageId, networkFailures);
      }

      for (IdentityKeyMismatch mismatch : identityMismatches) {
        database.addMismatchedIdentity(messageId, mismatch.getRecipientId(context), mismatch.getIdentityKey());
      }

      DatabaseFactory.getGroupReceiptDatabase(context).setUnidentified(successUnidentifiedStatus, messageId);

      if (proofRequired != null) {
        handleProofRequiredException(proofRequired, groupRecipient, threadId, messageId, true);
      }

      if (existingNetworkFailures.isEmpty() && networkFailures.isEmpty() && identityMismatches.isEmpty() && existingIdentityMismatches.isEmpty()) {
        database.markAsSent(messageId, true);

        markAttachmentsUploaded(messageId, message);

        if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
          database.markExpireStarted(messageId);
          ApplicationDependencies.getExpiringMessageManager()
                                 .scheduleDeletion(messageId, true, message.getExpiresIn());
        }

        if (message.isViewOnce()) {
          DatabaseFactory.getAttachmentDatabase(context).deleteAttachmentFilesForViewOnceMessage(messageId);
        }
      } else if (!networkFailures.isEmpty()) {
        throw new RetryLaterException();
      } else if (!identityMismatches.isEmpty()) {
        database.markAsSentFailed(messageId);
        notifyMediaMessageDeliveryFailed(context, messageId);

        Set<RecipientId> mismatchRecipientIds = Stream.of(identityMismatches)
                                                      .map(mismatch -> mismatch.getRecipientId(context))
                                                      .collect(Collectors.toSet());

        RetrieveProfileJob.enqueue(mismatchRecipientIds);
      }
    } catch (UntrustedIdentityException | UndeliverableMessageException e) {
      warn(TAG, String.valueOf(message.getSentTimeMillis()), e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public void onFailure() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
  }

  private List<SendMessageResult> deliver(OutgoingMediaMessage message, @NonNull Recipient groupRecipient, @NonNull List<Recipient> destinations)
      throws IOException, UntrustedIdentityException, UndeliverableMessageException
  {
    try {
      rotateSenderCertificateIfNecessary();

      GroupId.Push                               groupId            = groupRecipient.requireGroupId().requirePush();
      Optional<byte[]>                           profileKey         = getProfileKey(groupRecipient);
      Optional<Quote>                            quote              = getQuoteFor(message);
      Optional<SignalServiceDataMessage.Sticker> sticker            = getStickerFor(message);
      List<SharedContact>                        sharedContacts     = getSharedContactsFor(message);
      List<Preview>                              previews           = getPreviewsFor(message);
      List<SignalServiceDataMessage.Mention>     mentions           = getMentionsFor(message.getMentions());
      List<Attachment>                           attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
      List<SignalServiceAttachment>              attachmentPointers = getAttachmentPointersFor(attachments);
      boolean                                    isRecipientUpdate  = Stream.of(DatabaseFactory.getGroupReceiptDatabase(context).getGroupReceiptInfo(messageId))
                                                                            .anyMatch(info -> info.getStatus() > GroupReceiptDatabase.STATUS_UNDELIVERED);

      if (message.isGroup()) {
        OutgoingGroupUpdateMessage groupMessage = (OutgoingGroupUpdateMessage) message;

        if (groupMessage.isV2Group()) {
          MessageGroupContext.GroupV2Properties properties   = groupMessage.requireGroupV2Properties();
          GroupContextV2                        groupContext = properties.getGroupContext();
          SignalServiceGroupV2.Builder          builder      = SignalServiceGroupV2.newBuilder(properties.getGroupMasterKey())
                                                                                   .withRevision(groupContext.getRevision());

          ByteString groupChange = groupContext.getGroupChange();
          if (groupChange != null) {
            builder.withSignedGroupChange(groupChange.toByteArray());
          }

          SignalServiceGroupV2     group            = builder.build();
          SignalServiceDataMessage groupDataMessage = SignalServiceDataMessage.newBuilder()
                                                                              .withTimestamp(message.getSentTimeMillis())
                                                                              .withExpiration(groupRecipient.getExpiresInSeconds())
                                                                              .asGroupMessage(group)
                                                                              .build();
          return GroupSendUtil.sendResendableDataMessage(context, groupRecipient.requireGroupId().requireV2(), destinations, isRecipientUpdate, ContentHint.IMPLICIT, new MessageId(messageId, true), groupDataMessage);
        } else {
          throw new UndeliverableMessageException("Messages can no longer be sent to V1 groups!");
        }
      } else {
        SignalServiceDataMessage.Builder builder = SignalServiceDataMessage.newBuilder()
                                                                           .withTimestamp(message.getSentTimeMillis());

        GroupUtil.setDataMessageGroupContext(context, builder, groupId);

        SignalServiceDataMessage groupMessage = builder.withAttachments(attachmentPointers)
                                                       .withBody(message.getBody())
                                                       .withExpiration((int)(message.getExpiresIn() / 1000))
                                                       .withViewOnce(message.isViewOnce())
                                                       .asExpirationUpdate(message.isExpirationUpdate())
                                                       .withProfileKey(profileKey.orNull())
                                                       .withQuote(quote.orNull())
                                                       .withSticker(sticker.orNull())
                                                       .withSharedContacts(sharedContacts)
                                                       .withPreviews(previews)
                                                       .withMentions(mentions)
                                                       .build();

        Log.i(TAG, JobLogger.format(this, "Beginning message send."));

        return GroupSendUtil.sendResendableDataMessage(context,
                                                       groupRecipient.getGroupId().transform(GroupId::requireV2).orNull(),
                                                       destinations,
                                                       isRecipientUpdate,
                                                       ContentHint.RESENDABLE,
                                                       new MessageId(messageId, true),
                                                       groupMessage);
      }
    } catch (ServerRejectedException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  private @NonNull List<Recipient> getGroupMessageRecipients(@NonNull GroupId groupId, long messageId) {
    List<GroupReceiptInfo> destinations = DatabaseFactory.getGroupReceiptDatabase(context).getGroupReceiptInfo(messageId);

    if (!destinations.isEmpty()) {
        return RecipientUtil.getEligibleForSending(Stream.of(destinations)
                                                         .map(GroupReceiptInfo::getRecipientId)
                                                         .map(Recipient::resolved)
                                                         .toList());
    }

    List<Recipient> members = Stream.of(DatabaseFactory.getGroupDatabase(context)
                                                       .getGroupMembers(groupId, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF))
                                    .map(Recipient::resolve)
                                    .toList();

    if (members.size() > 0) {
      Log.w(TAG, "No destinations found for group message " + groupId + " using current group membership");
    }

    return RecipientUtil.getEligibleForSending(members);
  }

  public static long getMessageId(@NonNull Data data) {
    return data.getLong(KEY_MESSAGE_ID);
  }

  public static class Factory implements Job.Factory<PushGroupSendJob> {
    @Override
    public @NonNull PushGroupSendJob create(@NonNull Parameters parameters, @NonNull org.thoughtcrime.securesms.jobmanager.Data data) {
      String      raw    = data.getString(KEY_FILTER_RECIPIENT);
      RecipientId filter = raw != null ? RecipientId.from(raw) : null;

      return new PushGroupSendJob(parameters, data.getLong(KEY_MESSAGE_ID), filter);
    }
  }
}
