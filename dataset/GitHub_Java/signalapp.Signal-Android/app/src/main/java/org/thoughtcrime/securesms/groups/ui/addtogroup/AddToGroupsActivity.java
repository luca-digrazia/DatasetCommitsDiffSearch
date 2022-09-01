package org.thoughtcrime.securesms.groups.ui.addtogroup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProviders;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.ContactSelectionActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.groups.ui.addtogroup.AddToGroupViewModel.Event;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Group selection activity, will add a single member to selected groups.
 */
public final class AddToGroupsActivity extends ContactSelectionActivity {

  private static final int MINIMUM_GROUP_SELECT_SIZE = 1;

  private static final String EXTRA_RECIPIENT_ID = "RECIPIENT_ID";

  private View                next;
  private AddToGroupViewModel viewModel;

  public static Intent newIntent(@NonNull Context context,
                                 @NonNull RecipientId recipientId,
                                 @NonNull List<RecipientId> currentGroupsMemberOf)
  {
    Intent intent = new Intent(context, AddToGroupsActivity.class);

    intent.putExtra(ContactSelectionListFragment.REFRESHABLE, false);
    intent.putExtra(ContactSelectionListFragment.RECENTS, true);
    intent.putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.add_to_group_activity);
    intent.putExtra(EXTRA_RECIPIENT_ID, recipientId);

    intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, ContactsCursorLoader.DisplayMode.FLAG_ACTIVE_GROUPS);

    intent.putParcelableArrayListExtra(ContactSelectionListFragment.CURRENT_SELECTION, new ArrayList<>(currentGroupsMemberOf));

    return intent;
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    super.onCreate(bundle, ready);

    Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

    next = findViewById(R.id.next);

    getContactFilterView().setHint(contactsFragment.isMulti() ? R.string.AddToGroupActivity_add_to_groups : R.string.AddToGroupActivity_add_to_group);

    next.setVisibility(contactsFragment.isMulti() ? View.VISIBLE : View.GONE);

    disableNext();
    next.setOnClickListener(v -> handleNextPressed());

    AddToGroupViewModel.Factory factory = new AddToGroupViewModel.Factory(getRecipientId());
    viewModel = ViewModelProviders.of(this, factory)
                                  .get(AddToGroupViewModel.class);


    viewModel.getEvents().observe(this, event -> {
      if (event instanceof Event.CloseEvent) {
        finish();
      } else if (event instanceof Event.ToastEvent) {
        Toast.makeText(this, ((Event.ToastEvent) event).getMessage(), Toast.LENGTH_SHORT).show();
      } else if (event instanceof Event.AddToSingleGroupConfirmationEvent) {
        Event.AddToSingleGroupConfirmationEvent addEvent = (Event.AddToSingleGroupConfirmationEvent) event;
        new AlertDialog.Builder(this)
                       .setTitle(addEvent.getTitle())
                       .setMessage(addEvent.getMessage())
                       .setPositiveButton(R.string.AddToGroupActivity_add, (dialog, which) -> viewModel.onAddToGroupsConfirmed(addEvent))
                       .setNegativeButton(android.R.string.cancel, null)
                       .show();
      } else if (event instanceof Event.LegacyGroupDenialEvent) {
        Toast.makeText(this, R.string.AddToGroupActivity_this_person_cant_be_added_to_legacy_groups, Toast.LENGTH_SHORT).show();
      } else {
        throw new AssertionError();
      }
    });
  }

  private @NonNull RecipientId getRecipientId() {
    return getIntent().getParcelableExtra(EXTRA_RECIPIENT_ID);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBeforeContactSelected(Optional<RecipientId> recipientId, String number, Consumer<Boolean> callback) {
    if (contactsFragment.isMulti()) {
      throw new UnsupportedOperationException("Not yet built to handle multi-select.");
//      if (contactsFragment.hasQueryFilter()) {
//        getToolbar().clear();
//      }
//
//      if (contactsFragment.getSelectedContactsCount() >= MINIMUM_GROUP_SELECT_SIZE) {
//        enableNext();
//      }
    } else {
      if (recipientId.isPresent()) {
        viewModel.onContinueWithSelection(Collections.singletonList(recipientId.get()));
      }
    }

    callback.accept(true);
  }

  @Override
  public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      getContactFilterView().clear();
    }

    if (contactsFragment.getSelectedContactsCount() < MINIMUM_GROUP_SELECT_SIZE) {
      disableNext();
    }
  }

  @Override
  public void onSelectionChanged() {
  }

  private void enableNext() {
    next.setEnabled(true);
    next.animate().alpha(1f);
  }

  private void disableNext() {
    next.setEnabled(false);
    next.animate().alpha(0.5f);
  }

  private void handleNextPressed() {
    List<RecipientId> groupsRecipientIds = Stream.of(contactsFragment.getSelectedContacts())
                                                 .map(selectedContact -> selectedContact.getOrCreateRecipientId(this))
                                                 .toList();

    viewModel.onContinueWithSelection(groupsRecipientIds);
  }
}
