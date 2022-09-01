/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.plugins.netflow.v9;

public final class NetFlowV9Journal {
  private NetFlowV9Journal() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  public interface RawNetflowV9OrBuilder extends
      // @@protoc_insertion_point(interface_extends:org.graylog.plugins.netflow.v9.RawNetflowV9)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <pre>
     * all applicable templates that are referenced by the packets
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
     */
    int getTemplatesCount();
    /**
     * <pre>
     * all applicable templates that are referenced by the packets
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
     */
    boolean containsTemplates(
        int key);
    /**
     * Use {@link #getTemplatesMap()} instead.
     */
    @java.lang.Deprecated
    java.util.Map<java.lang.Integer, com.google.protobuf.ByteString>
    getTemplates();
    /**
     * <pre>
     * all applicable templates that are referenced by the packets
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
     */
    java.util.Map<java.lang.Integer, com.google.protobuf.ByteString>
    getTemplatesMap();
    /**
     * <pre>
     * all applicable templates that are referenced by the packets
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
     */

    com.google.protobuf.ByteString getTemplatesOrDefault(
        int key,
        com.google.protobuf.ByteString defaultValue);
    /**
     * <pre>
     * all applicable templates that are referenced by the packets
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
     */

    com.google.protobuf.ByteString getTemplatesOrThrow(
        int key);

    /**
     * <pre>
     * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
     */
    int getOptionTemplateCount();
    /**
     * <pre>
     * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
     */
    boolean containsOptionTemplate(
        int key);
    /**
     * Use {@link #getOptionTemplateMap()} instead.
     */
    @java.lang.Deprecated
    java.util.Map<java.lang.Integer, com.google.protobuf.ByteString>
    getOptionTemplate();
    /**
     * <pre>
     * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
     */
    java.util.Map<java.lang.Integer, com.google.protobuf.ByteString>
    getOptionTemplateMap();
    /**
     * <pre>
     * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
     */

    com.google.protobuf.ByteString getOptionTemplateOrDefault(
        int key,
        com.google.protobuf.ByteString defaultValue);
    /**
     * <pre>
     * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
     */

    com.google.protobuf.ByteString getOptionTemplateOrThrow(
        int key);

    /**
     * <pre>
     * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
     * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
     * this will be a single packet sent by the exporter
     * </pre>
     *
     * <code>repeated bytes packets = 3;</code>
     */
    java.util.List<com.google.protobuf.ByteString> getPacketsList();
    /**
     * <pre>
     * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
     * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
     * this will be a single packet sent by the exporter
     * </pre>
     *
     * <code>repeated bytes packets = 3;</code>
     */
    int getPacketsCount();
    /**
     * <pre>
     * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
     * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
     * this will be a single packet sent by the exporter
     * </pre>
     *
     * <code>repeated bytes packets = 3;</code>
     */
    com.google.protobuf.ByteString getPackets(int index);
  }
  /**
   * Protobuf type {@code org.graylog.plugins.netflow.v9.RawNetflowV9}
   */
  public  static final class RawNetflowV9 extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:org.graylog.plugins.netflow.v9.RawNetflowV9)
      RawNetflowV9OrBuilder {
    // Use RawNetflowV9.newBuilder() to construct.
    private RawNetflowV9(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private RawNetflowV9() {
      packets_ = java.util.Collections.emptyList();
    }

    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private RawNetflowV9(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!parseUnknownField(input, unknownFields,
                                     extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
            case 10: {
              if (!((mutable_bitField0_ & 0x00000001) == 0x00000001)) {
                templates_ = com.google.protobuf.MapField.newMapField(
                    TemplatesDefaultEntryHolder.defaultEntry);
                mutable_bitField0_ |= 0x00000001;
              }
              com.google.protobuf.MapEntry<java.lang.Integer, com.google.protobuf.ByteString>
              templates = input.readMessage(
                  TemplatesDefaultEntryHolder.defaultEntry.getParserForType(), extensionRegistry);
              templates_.getMutableMap().put(templates.getKey(), templates.getValue());
              break;
            }
            case 18: {
              if (!((mutable_bitField0_ & 0x00000002) == 0x00000002)) {
                optionTemplate_ = com.google.protobuf.MapField.newMapField(
                    OptionTemplateDefaultEntryHolder.defaultEntry);
                mutable_bitField0_ |= 0x00000002;
              }
              com.google.protobuf.MapEntry<java.lang.Integer, com.google.protobuf.ByteString>
              optionTemplate = input.readMessage(
                  OptionTemplateDefaultEntryHolder.defaultEntry.getParserForType(), extensionRegistry);
              optionTemplate_.getMutableMap().put(optionTemplate.getKey(), optionTemplate.getValue());
              break;
            }
            case 26: {
              if (!((mutable_bitField0_ & 0x00000004) == 0x00000004)) {
                packets_ = new java.util.ArrayList<com.google.protobuf.ByteString>();
                mutable_bitField0_ |= 0x00000004;
              }
              packets_.add(input.readBytes());
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        if (((mutable_bitField0_ & 0x00000004) == 0x00000004)) {
          packets_ = java.util.Collections.unmodifiableList(packets_);
        }
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return org.graylog.plugins.netflow.v9.NetFlowV9Journal.internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_descriptor;
    }

    @SuppressWarnings({"rawtypes"})
    protected com.google.protobuf.MapField internalGetMapField(
        int number) {
      switch (number) {
        case 1:
          return internalGetTemplates();
        case 2:
          return internalGetOptionTemplate();
        default:
          throw new RuntimeException(
              "Invalid map field number: " + number);
      }
    }
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return org.graylog.plugins.netflow.v9.NetFlowV9Journal.internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9.class, org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9.Builder.class);
    }

    public static final int TEMPLATES_FIELD_NUMBER = 1;
    private static final class TemplatesDefaultEntryHolder {
      static final com.google.protobuf.MapEntry<
          java.lang.Integer, com.google.protobuf.ByteString> defaultEntry =
              com.google.protobuf.MapEntry
              .<java.lang.Integer, com.google.protobuf.ByteString>newDefaultInstance(
                  org.graylog.plugins.netflow.v9.NetFlowV9Journal.internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_TemplatesEntry_descriptor, 
                  com.google.protobuf.WireFormat.FieldType.UINT32,
                  0,
                  com.google.protobuf.WireFormat.FieldType.BYTES,
                  com.google.protobuf.ByteString.EMPTY);
    }
    private com.google.protobuf.MapField<
        java.lang.Integer, com.google.protobuf.ByteString> templates_;
    private com.google.protobuf.MapField<java.lang.Integer, com.google.protobuf.ByteString>
    internalGetTemplates() {
      if (templates_ == null) {
        return com.google.protobuf.MapField.emptyMapField(
            TemplatesDefaultEntryHolder.defaultEntry);
      }
      return templates_;
    }

    public int getTemplatesCount() {
      return internalGetTemplates().getMap().size();
    }
    /**
     * <pre>
     * all applicable templates that are referenced by the packets
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
     */

    public boolean containsTemplates(
        int key) {
      
      return internalGetTemplates().getMap().containsKey(key);
    }
    /**
     * Use {@link #getTemplatesMap()} instead.
     */
    @java.lang.Deprecated
    public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> getTemplates() {
      return getTemplatesMap();
    }
    /**
     * <pre>
     * all applicable templates that are referenced by the packets
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
     */

    public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> getTemplatesMap() {
      return internalGetTemplates().getMap();
    }
    /**
     * <pre>
     * all applicable templates that are referenced by the packets
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
     */

    public com.google.protobuf.ByteString getTemplatesOrDefault(
        int key,
        com.google.protobuf.ByteString defaultValue) {
      
      java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> map =
          internalGetTemplates().getMap();
      return map.containsKey(key) ? map.get(key) : defaultValue;
    }
    /**
     * <pre>
     * all applicable templates that are referenced by the packets
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
     */

    public com.google.protobuf.ByteString getTemplatesOrThrow(
        int key) {
      
      java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> map =
          internalGetTemplates().getMap();
      if (!map.containsKey(key)) {
        throw new java.lang.IllegalArgumentException();
      }
      return map.get(key);
    }

    public static final int OPTIONTEMPLATE_FIELD_NUMBER = 2;
    private static final class OptionTemplateDefaultEntryHolder {
      static final com.google.protobuf.MapEntry<
          java.lang.Integer, com.google.protobuf.ByteString> defaultEntry =
              com.google.protobuf.MapEntry
              .<java.lang.Integer, com.google.protobuf.ByteString>newDefaultInstance(
                  org.graylog.plugins.netflow.v9.NetFlowV9Journal.internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_OptionTemplateEntry_descriptor, 
                  com.google.protobuf.WireFormat.FieldType.UINT32,
                  0,
                  com.google.protobuf.WireFormat.FieldType.BYTES,
                  com.google.protobuf.ByteString.EMPTY);
    }
    private com.google.protobuf.MapField<
        java.lang.Integer, com.google.protobuf.ByteString> optionTemplate_;
    private com.google.protobuf.MapField<java.lang.Integer, com.google.protobuf.ByteString>
    internalGetOptionTemplate() {
      if (optionTemplate_ == null) {
        return com.google.protobuf.MapField.emptyMapField(
            OptionTemplateDefaultEntryHolder.defaultEntry);
      }
      return optionTemplate_;
    }

    public int getOptionTemplateCount() {
      return internalGetOptionTemplate().getMap().size();
    }
    /**
     * <pre>
     * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
     */

    public boolean containsOptionTemplate(
        int key) {
      
      return internalGetOptionTemplate().getMap().containsKey(key);
    }
    /**
     * Use {@link #getOptionTemplateMap()} instead.
     */
    @java.lang.Deprecated
    public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> getOptionTemplate() {
      return getOptionTemplateMap();
    }
    /**
     * <pre>
     * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
     */

    public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> getOptionTemplateMap() {
      return internalGetOptionTemplate().getMap();
    }
    /**
     * <pre>
     * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
     */

    public com.google.protobuf.ByteString getOptionTemplateOrDefault(
        int key,
        com.google.protobuf.ByteString defaultValue) {
      
      java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> map =
          internalGetOptionTemplate().getMap();
      return map.containsKey(key) ? map.get(key) : defaultValue;
    }
    /**
     * <pre>
     * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
     * </pre>
     *
     * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
     */

    public com.google.protobuf.ByteString getOptionTemplateOrThrow(
        int key) {
      
      java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> map =
          internalGetOptionTemplate().getMap();
      if (!map.containsKey(key)) {
        throw new java.lang.IllegalArgumentException();
      }
      return map.get(key);
    }

    public static final int PACKETS_FIELD_NUMBER = 3;
    private java.util.List<com.google.protobuf.ByteString> packets_;
    /**
     * <pre>
     * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
     * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
     * this will be a single packet sent by the exporter
     * </pre>
     *
     * <code>repeated bytes packets = 3;</code>
     */
    public java.util.List<com.google.protobuf.ByteString>
        getPacketsList() {
      return packets_;
    }
    /**
     * <pre>
     * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
     * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
     * this will be a single packet sent by the exporter
     * </pre>
     *
     * <code>repeated bytes packets = 3;</code>
     */
    public int getPacketsCount() {
      return packets_.size();
    }
    /**
     * <pre>
     * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
     * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
     * this will be a single packet sent by the exporter
     * </pre>
     *
     * <code>repeated bytes packets = 3;</code>
     */
    public com.google.protobuf.ByteString getPackets(int index) {
      return packets_.get(index);
    }

    private byte memoizedIsInitialized = -1;
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      for (java.util.Map.Entry<java.lang.Integer, com.google.protobuf.ByteString> entry
           : internalGetTemplates().getMap().entrySet()) {
        com.google.protobuf.MapEntry<java.lang.Integer, com.google.protobuf.ByteString>
        templates = TemplatesDefaultEntryHolder.defaultEntry.newBuilderForType()
            .setKey(entry.getKey())
            .setValue(entry.getValue())
            .build();
        output.writeMessage(1, templates);
      }
      for (java.util.Map.Entry<java.lang.Integer, com.google.protobuf.ByteString> entry
           : internalGetOptionTemplate().getMap().entrySet()) {
        com.google.protobuf.MapEntry<java.lang.Integer, com.google.protobuf.ByteString>
        optionTemplate = OptionTemplateDefaultEntryHolder.defaultEntry.newBuilderForType()
            .setKey(entry.getKey())
            .setValue(entry.getValue())
            .build();
        output.writeMessage(2, optionTemplate);
      }
      for (int i = 0; i < packets_.size(); i++) {
        output.writeBytes(3, packets_.get(i));
      }
      unknownFields.writeTo(output);
    }

    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      for (java.util.Map.Entry<java.lang.Integer, com.google.protobuf.ByteString> entry
           : internalGetTemplates().getMap().entrySet()) {
        com.google.protobuf.MapEntry<java.lang.Integer, com.google.protobuf.ByteString>
        templates = TemplatesDefaultEntryHolder.defaultEntry.newBuilderForType()
            .setKey(entry.getKey())
            .setValue(entry.getValue())
            .build();
        size += com.google.protobuf.CodedOutputStream
            .computeMessageSize(1, templates);
      }
      for (java.util.Map.Entry<java.lang.Integer, com.google.protobuf.ByteString> entry
           : internalGetOptionTemplate().getMap().entrySet()) {
        com.google.protobuf.MapEntry<java.lang.Integer, com.google.protobuf.ByteString>
        optionTemplate = OptionTemplateDefaultEntryHolder.defaultEntry.newBuilderForType()
            .setKey(entry.getKey())
            .setValue(entry.getValue())
            .build();
        size += com.google.protobuf.CodedOutputStream
            .computeMessageSize(2, optionTemplate);
      }
      {
        int dataSize = 0;
        for (int i = 0; i < packets_.size(); i++) {
          dataSize += com.google.protobuf.CodedOutputStream
            .computeBytesSizeNoTag(packets_.get(i));
        }
        size += dataSize;
        size += 1 * getPacketsList().size();
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    private static final long serialVersionUID = 0L;
    @java.lang.Override
    public boolean equals(final java.lang.Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9)) {
        return super.equals(obj);
      }
      org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 other = (org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9) obj;

      boolean result = true;
      result = result && internalGetTemplates().equals(
          other.internalGetTemplates());
      result = result && internalGetOptionTemplate().equals(
          other.internalGetOptionTemplate());
      result = result && getPacketsList()
          .equals(other.getPacketsList());
      result = result && unknownFields.equals(other.unknownFields);
      return result;
    }

    @java.lang.Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptorForType().hashCode();
      if (!internalGetTemplates().getMap().isEmpty()) {
        hash = (37 * hash) + TEMPLATES_FIELD_NUMBER;
        hash = (53 * hash) + internalGetTemplates().hashCode();
      }
      if (!internalGetOptionTemplate().getMap().isEmpty()) {
        hash = (37 * hash) + OPTIONTEMPLATE_FIELD_NUMBER;
        hash = (53 * hash) + internalGetOptionTemplate().hashCode();
      }
      if (getPacketsCount() > 0) {
        hash = (37 * hash) + PACKETS_FIELD_NUMBER;
        hash = (53 * hash) + getPacketsList().hashCode();
      }
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code org.graylog.plugins.netflow.v9.RawNetflowV9}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:org.graylog.plugins.netflow.v9.RawNetflowV9)
        org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9OrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return org.graylog.plugins.netflow.v9.NetFlowV9Journal.internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_descriptor;
      }

      @SuppressWarnings({"rawtypes"})
      protected com.google.protobuf.MapField internalGetMapField(
          int number) {
        switch (number) {
          case 1:
            return internalGetTemplates();
          case 2:
            return internalGetOptionTemplate();
          default:
            throw new RuntimeException(
                "Invalid map field number: " + number);
        }
      }
      @SuppressWarnings({"rawtypes"})
      protected com.google.protobuf.MapField internalGetMutableMapField(
          int number) {
        switch (number) {
          case 1:
            return internalGetMutableTemplates();
          case 2:
            return internalGetMutableOptionTemplate();
          default:
            throw new RuntimeException(
                "Invalid map field number: " + number);
        }
      }
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return org.graylog.plugins.netflow.v9.NetFlowV9Journal.internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9.class, org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9.Builder.class);
      }

      // Construct using org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      public Builder clear() {
        super.clear();
        internalGetMutableTemplates().clear();
        internalGetMutableOptionTemplate().clear();
        packets_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000004);
        return this;
      }

      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return org.graylog.plugins.netflow.v9.NetFlowV9Journal.internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_descriptor;
      }

      public org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 getDefaultInstanceForType() {
        return org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9.getDefaultInstance();
      }

      public org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 build() {
        org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      public org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 buildPartial() {
        org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 result = new org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9(this);
        int from_bitField0_ = bitField0_;
        result.templates_ = internalGetTemplates();
        result.templates_.makeImmutable();
        result.optionTemplate_ = internalGetOptionTemplate();
        result.optionTemplate_.makeImmutable();
        if (((bitField0_ & 0x00000004) == 0x00000004)) {
          packets_ = java.util.Collections.unmodifiableList(packets_);
          bitField0_ = (bitField0_ & ~0x00000004);
        }
        result.packets_ = packets_;
        onBuilt();
        return result;
      }

      public Builder clone() {
        return (Builder) super.clone();
      }
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          Object value) {
        return (Builder) super.setField(field, value);
      }
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return (Builder) super.clearField(field);
      }
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return (Builder) super.clearOneof(oneof);
      }
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, Object value) {
        return (Builder) super.setRepeatedField(field, index, value);
      }
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          Object value) {
        return (Builder) super.addRepeatedField(field, value);
      }
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9) {
          return mergeFrom((org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 other) {
        if (other == org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9.getDefaultInstance()) return this;
        internalGetMutableTemplates().mergeFrom(
            other.internalGetTemplates());
        internalGetMutableOptionTemplate().mergeFrom(
            other.internalGetOptionTemplate());
        if (!other.packets_.isEmpty()) {
          if (packets_.isEmpty()) {
            packets_ = other.packets_;
            bitField0_ = (bitField0_ & ~0x00000004);
          } else {
            ensurePacketsIsMutable();
            packets_.addAll(other.packets_);
          }
          onChanged();
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      public final boolean isInitialized() {
        return true;
      }

      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      private com.google.protobuf.MapField<
          java.lang.Integer, com.google.protobuf.ByteString> templates_;
      private com.google.protobuf.MapField<java.lang.Integer, com.google.protobuf.ByteString>
      internalGetTemplates() {
        if (templates_ == null) {
          return com.google.protobuf.MapField.emptyMapField(
              TemplatesDefaultEntryHolder.defaultEntry);
        }
        return templates_;
      }
      private com.google.protobuf.MapField<java.lang.Integer, com.google.protobuf.ByteString>
      internalGetMutableTemplates() {
        onChanged();;
        if (templates_ == null) {
          templates_ = com.google.protobuf.MapField.newMapField(
              TemplatesDefaultEntryHolder.defaultEntry);
        }
        if (!templates_.isMutable()) {
          templates_ = templates_.copy();
        }
        return templates_;
      }

      public int getTemplatesCount() {
        return internalGetTemplates().getMap().size();
      }
      /**
       * <pre>
       * all applicable templates that are referenced by the packets
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
       */

      public boolean containsTemplates(
          int key) {
        
        return internalGetTemplates().getMap().containsKey(key);
      }
      /**
       * Use {@link #getTemplatesMap()} instead.
       */
      @java.lang.Deprecated
      public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> getTemplates() {
        return getTemplatesMap();
      }
      /**
       * <pre>
       * all applicable templates that are referenced by the packets
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
       */

      public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> getTemplatesMap() {
        return internalGetTemplates().getMap();
      }
      /**
       * <pre>
       * all applicable templates that are referenced by the packets
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
       */

      public com.google.protobuf.ByteString getTemplatesOrDefault(
          int key,
          com.google.protobuf.ByteString defaultValue) {
        
        java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> map =
            internalGetTemplates().getMap();
        return map.containsKey(key) ? map.get(key) : defaultValue;
      }
      /**
       * <pre>
       * all applicable templates that are referenced by the packets
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
       */

      public com.google.protobuf.ByteString getTemplatesOrThrow(
          int key) {
        
        java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> map =
            internalGetTemplates().getMap();
        if (!map.containsKey(key)) {
          throw new java.lang.IllegalArgumentException();
        }
        return map.get(key);
      }

      public Builder clearTemplates() {
        getMutableTemplates().clear();
        return this;
      }
      /**
       * <pre>
       * all applicable templates that are referenced by the packets
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
       */

      public Builder removeTemplates(
          int key) {
        
        getMutableTemplates().remove(key);
        return this;
      }
      /**
       * Use alternate mutation accessors instead.
       */
      @java.lang.Deprecated
      public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString>
      getMutableTemplates() {
        return internalGetMutableTemplates().getMutableMap();
      }
      /**
       * <pre>
       * all applicable templates that are referenced by the packets
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
       */
      public Builder putTemplates(
          int key,
          com.google.protobuf.ByteString value) {
        
        if (value == null) { throw new java.lang.NullPointerException(); }
        getMutableTemplates().put(key, value);
        return this;
      }
      /**
       * <pre>
       * all applicable templates that are referenced by the packets
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; templates = 1;</code>
       */

      public Builder putAllTemplates(
          java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> values) {
        getMutableTemplates().putAll(values);
        return this;
      }

      private com.google.protobuf.MapField<
          java.lang.Integer, com.google.protobuf.ByteString> optionTemplate_;
      private com.google.protobuf.MapField<java.lang.Integer, com.google.protobuf.ByteString>
      internalGetOptionTemplate() {
        if (optionTemplate_ == null) {
          return com.google.protobuf.MapField.emptyMapField(
              OptionTemplateDefaultEntryHolder.defaultEntry);
        }
        return optionTemplate_;
      }
      private com.google.protobuf.MapField<java.lang.Integer, com.google.protobuf.ByteString>
      internalGetMutableOptionTemplate() {
        onChanged();;
        if (optionTemplate_ == null) {
          optionTemplate_ = com.google.protobuf.MapField.newMapField(
              OptionTemplateDefaultEntryHolder.defaultEntry);
        }
        if (!optionTemplate_.isMutable()) {
          optionTemplate_ = optionTemplate_.copy();
        }
        return optionTemplate_;
      }

      public int getOptionTemplateCount() {
        return internalGetOptionTemplate().getMap().size();
      }
      /**
       * <pre>
       * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
       */

      public boolean containsOptionTemplate(
          int key) {
        
        return internalGetOptionTemplate().getMap().containsKey(key);
      }
      /**
       * Use {@link #getOptionTemplateMap()} instead.
       */
      @java.lang.Deprecated
      public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> getOptionTemplate() {
        return getOptionTemplateMap();
      }
      /**
       * <pre>
       * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
       */

      public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> getOptionTemplateMap() {
        return internalGetOptionTemplate().getMap();
      }
      /**
       * <pre>
       * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
       */

      public com.google.protobuf.ByteString getOptionTemplateOrDefault(
          int key,
          com.google.protobuf.ByteString defaultValue) {
        
        java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> map =
            internalGetOptionTemplate().getMap();
        return map.containsKey(key) ? map.get(key) : defaultValue;
      }
      /**
       * <pre>
       * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
       */

      public com.google.protobuf.ByteString getOptionTemplateOrThrow(
          int key) {
        
        java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> map =
            internalGetOptionTemplate().getMap();
        if (!map.containsKey(key)) {
          throw new java.lang.IllegalArgumentException();
        }
        return map.get(key);
      }

      public Builder clearOptionTemplate() {
        getMutableOptionTemplate().clear();
        return this;
      }
      /**
       * <pre>
       * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
       */

      public Builder removeOptionTemplate(
          int key) {
        
        getMutableOptionTemplate().remove(key);
        return this;
      }
      /**
       * Use alternate mutation accessors instead.
       */
      @java.lang.Deprecated
      public java.util.Map<java.lang.Integer, com.google.protobuf.ByteString>
      getMutableOptionTemplate() {
        return internalGetMutableOptionTemplate().getMutableMap();
      }
      /**
       * <pre>
       * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
       */
      public Builder putOptionTemplate(
          int key,
          com.google.protobuf.ByteString value) {
        
        if (value == null) { throw new java.lang.NullPointerException(); }
        getMutableOptionTemplate().put(key, value);
        return this;
      }
      /**
       * <pre>
       * there is only one option template, but we need to know its template id, so a map is the easiest way to find it
       * </pre>
       *
       * <code>map&lt;uint32, bytes&gt; optionTemplate = 2;</code>
       */

      public Builder putAllOptionTemplate(
          java.util.Map<java.lang.Integer, com.google.protobuf.ByteString> values) {
        getMutableOptionTemplate().putAll(values);
        return this;
      }

      private java.util.List<com.google.protobuf.ByteString> packets_ = java.util.Collections.emptyList();
      private void ensurePacketsIsMutable() {
        if (!((bitField0_ & 0x00000004) == 0x00000004)) {
          packets_ = new java.util.ArrayList<com.google.protobuf.ByteString>(packets_);
          bitField0_ |= 0x00000004;
         }
      }
      /**
       * <pre>
       * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
       * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
       * this will be a single packet sent by the exporter
       * </pre>
       *
       * <code>repeated bytes packets = 3;</code>
       */
      public java.util.List<com.google.protobuf.ByteString>
          getPacketsList() {
        return java.util.Collections.unmodifiableList(packets_);
      }
      /**
       * <pre>
       * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
       * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
       * this will be a single packet sent by the exporter
       * </pre>
       *
       * <code>repeated bytes packets = 3;</code>
       */
      public int getPacketsCount() {
        return packets_.size();
      }
      /**
       * <pre>
       * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
       * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
       * this will be a single packet sent by the exporter
       * </pre>
       *
       * <code>repeated bytes packets = 3;</code>
       */
      public com.google.protobuf.ByteString getPackets(int index) {
        return packets_.get(index);
      }
      /**
       * <pre>
       * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
       * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
       * this will be a single packet sent by the exporter
       * </pre>
       *
       * <code>repeated bytes packets = 3;</code>
       */
      public Builder setPackets(
          int index, com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensurePacketsIsMutable();
        packets_.set(index, value);
        onChanged();
        return this;
      }
      /**
       * <pre>
       * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
       * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
       * this will be a single packet sent by the exporter
       * </pre>
       *
       * <code>repeated bytes packets = 3;</code>
       */
      public Builder addPackets(com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  ensurePacketsIsMutable();
        packets_.add(value);
        onChanged();
        return this;
      }
      /**
       * <pre>
       * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
       * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
       * this will be a single packet sent by the exporter
       * </pre>
       *
       * <code>repeated bytes packets = 3;</code>
       */
      public Builder addAllPackets(
          java.lang.Iterable<? extends com.google.protobuf.ByteString> values) {
        ensurePacketsIsMutable();
        com.google.protobuf.AbstractMessageLite.Builder.addAll(
            values, packets_);
        onChanged();
        return this;
      }
      /**
       * <pre>
       * the raw packets as received. it might contain templates as well, but even if it does the above fields will have that information, too
       * in case we previously buffered flows, this may contain more than one flow. in situations when we have all templates already
       * this will be a single packet sent by the exporter
       * </pre>
       *
       * <code>repeated bytes packets = 3;</code>
       */
      public Builder clearPackets() {
        packets_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000004);
        onChanged();
        return this;
      }
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:org.graylog.plugins.netflow.v9.RawNetflowV9)
    }

    // @@protoc_insertion_point(class_scope:org.graylog.plugins.netflow.v9.RawNetflowV9)
    private static final org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9();
    }

    public static org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    @java.lang.Deprecated public static final com.google.protobuf.Parser<RawNetflowV9>
        PARSER = new com.google.protobuf.AbstractParser<RawNetflowV9>() {
      public RawNetflowV9 parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
          return new RawNetflowV9(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<RawNetflowV9> parser() {
      return PARSER;
    }

    @java.lang.Override
    public com.google.protobuf.Parser<RawNetflowV9> getParserForType() {
      return PARSER;
    }

    public org.graylog.plugins.netflow.v9.NetFlowV9Journal.RawNetflowV9 getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_TemplatesEntry_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_TemplatesEntry_fieldAccessorTable;
  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_OptionTemplateEntry_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_OptionTemplateEntry_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n#src/main/resources/netflow_v9.proto\022\036o" +
      "rg.graylog.plugins.netflow.v9\"\262\002\n\014RawNet" +
      "flowV9\022N\n\ttemplates\030\001 \003(\0132;.org.graylog." +
      "plugins.netflow.v9.RawNetflowV9.Template" +
      "sEntry\022X\n\016optionTemplate\030\002 \003(\0132@.org.gra" +
      "ylog.plugins.netflow.v9.RawNetflowV9.Opt" +
      "ionTemplateEntry\022\017\n\007packets\030\003 \003(\014\0320\n\016Tem" +
      "platesEntry\022\013\n\003key\030\001 \001(\r\022\r\n\005value\030\002 \001(\014:" +
      "\0028\001\0325\n\023OptionTemplateEntry\022\013\n\003key\030\001 \001(\r\022" +
      "\r\n\005value\030\002 \001(\014:\0028\001B2\n\036org.graylog.plugin",
      "s.netflow.v9B\020NetFlowV9Journal"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_descriptor,
        new java.lang.String[] { "Templates", "OptionTemplate", "Packets", });
    internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_TemplatesEntry_descriptor =
      internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_descriptor.getNestedTypes().get(0);
    internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_TemplatesEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_TemplatesEntry_descriptor,
        new java.lang.String[] { "Key", "Value", });
    internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_OptionTemplateEntry_descriptor =
      internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_descriptor.getNestedTypes().get(1);
    internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_OptionTemplateEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_org_graylog_plugins_netflow_v9_RawNetflowV9_OptionTemplateEntry_descriptor,
        new java.lang.String[] { "Key", "Value", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
