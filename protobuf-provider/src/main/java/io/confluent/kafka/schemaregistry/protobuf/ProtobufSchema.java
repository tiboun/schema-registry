/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package io.confluent.kafka.schemaregistry.protobuf;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.AnyProto;
import com.google.protobuf.ApiProto;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto.ReservedRange;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldOptions.CType;
import com.google.protobuf.DescriptorProtos.FieldOptions.JSType;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions.OptimizeMode;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodOptions.IdempotencyLevel;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DurationProto;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.EmptyProto;
import com.google.protobuf.FieldMaskProto;
import com.google.protobuf.SourceContextProto;
import com.google.protobuf.StructProto;
import com.google.protobuf.TimestampProto;
import com.google.protobuf.TypeProto;
import com.google.protobuf.WrappersProto;
import com.google.type.CalendarPeriodProto;
import com.google.type.ColorProto;
import com.google.type.DateProto;
import com.google.type.DateTimeProto;
import com.google.type.DayOfWeekProto;
import com.google.type.ExprProto;
import com.google.type.FractionProto;
import com.google.type.IntervalProto;
import com.google.type.LatLngProto;
import com.google.type.MoneyProto;
import com.google.type.MonthProto;
import com.google.type.PhoneNumberProto;
import com.google.type.PostalAddressProto;
import com.google.type.QuaternionProto;
import com.google.type.TimeOfDayProto;
import com.squareup.wire.Syntax;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.OptionElement.Kind;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import com.squareup.wire.schema.internal.parser.ReservedElement;
import com.squareup.wire.schema.internal.parser.RpcElement;
import com.squareup.wire.schema.internal.parser.ServiceElement;
import com.squareup.wire.schema.internal.parser.TypeElement;
import io.confluent.kafka.schemaregistry.protobuf.dynamic.ServiceDefinition;
import io.confluent.protobuf.MetaProto;
import io.confluent.protobuf.MetaProto.Meta;
import io.confluent.protobuf.type.DecimalProto;
import java.util.LinkedHashMap;
import kotlin.ranges.IntRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.protobuf.diff.Difference;
import io.confluent.kafka.schemaregistry.protobuf.diff.SchemaDiff;
import io.confluent.kafka.schemaregistry.protobuf.dynamic.DynamicSchema;
import io.confluent.kafka.schemaregistry.protobuf.dynamic.EnumDefinition;
import io.confluent.kafka.schemaregistry.protobuf.dynamic.MessageDefinition;

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

public class ProtobufSchema implements ParsedSchema {

  private static final Logger log = LoggerFactory.getLogger(ProtobufSchema.class);

  public static final String TYPE = "PROTOBUF";

  public static final String SERIALIZED_FORMAT = "serialized";

  public static final String PROTO2 = "proto2";
  public static final String PROTO3 = "proto3";

  public static final String DOC_FIELD = "doc";
  public static final String PARAMS_FIELD = "params";

  public static final String DEFAULT_NAME = "default";
  public static final String MAP_ENTRY_SUFFIX = "Entry";  // Suffix used by protoc
  public static final String KEY_FIELD = "key";
  public static final String VALUE_FIELD = "value";

  private static final String CONFLUENT_FILE_META = "confluent.file_meta";
  private static final String CONFLUENT_MESSAGE_META = "confluent.message_meta";
  private static final String CONFLUENT_FIELD_META = "confluent.field_meta";
  private static final String CONFLUENT_ENUM_META = "confluent.enum_meta";
  private static final String CONFLUENT_ENUM_VALUE_META = "confluent.enum_value_meta";

  private static final String JAVA_PACKAGE = "java_package";
  private static final String JAVA_OUTER_CLASSNAME = "java_outer_classname";
  private static final String JAVA_MULTIPLE_FILES = "java_multiple_files";
  private static final String JAVA_STRING_CHECK_UTF8 = "java_string_check_utf8";
  private static final String OPTIMIZE_FOR = "optimize_for";
  private static final String GO_PACKAGE = "go_package";
  private static final String CC_GENERIC_SERVICES = "cc_generic_services";
  private static final String JAVA_GENERIC_SERVICES = "java_generic_services";
  private static final String PY_GENERIC_SERVICES = "py_generic_services";
  private static final String PHP_GENERIC_SERVICES = "php_generic_services";
  private static final String DEPRECATED = "deprecated";
  private static final String CC_ENABLE_ARENAS = "cc_enable_arenas";
  private static final String OBJC_CLASS_PREFIX = "objc_class_prefix";
  private static final String CSHARP_NAMESPACE = "csharp_namespace";
  private static final String SWIFT_PREFIX = "swift_prefix";
  private static final String PHP_CLASS_PREFIX = "php_class_prefix";
  private static final String PHP_NAMESPACE = "php_namespace";
  private static final String PHP_METADATA_NAMESPACE = "php_metadata_namespace";
  private static final String RUBY_PACKAGE = "ruby_package";

  private static final String NO_STANDARD_DESCRIPTOR_ACCESSOR = "no_standard_descriptor_accessor";
  private static final String MAP_ENTRY = "map_entry";

  private static final String CTYPE = "ctype";
  private static final String PACKED = "packed";
  private static final String JSTYPE = "jstype";

  private static final String ALLOW_ALIAS = "allow_alias";

  private static final String IDEMPOTENCY_LEVEL = "idempotency_level";

  public static final Location DEFAULT_LOCATION = Location.get("");

  public static final String CFLT_META_LOCATION = "confluent/meta.proto";
  public static final String CFLT_DECIMAL_LOCATION = "confluent/type/decimal.proto";
  public static final String CALENDAR_PERIOD_LOCATION = "google/type/calendar_period.proto";
  public static final String COLOR_LOCATION = "google/type/color.proto";
  public static final String DATE_LOCATION = "google/type/date.proto";
  public static final String DATETIME_LOCATION = "google/type/datetime.proto";
  public static final String DAY_OF_WEEK_LOCATION = "google/type/dayofweek.proto";
  public static final String DECIMAL_LOCATION = "google/type/decimal.proto";
  public static final String EXPR_LOCATION = "google/type/expr.proto";
  public static final String FRACTION_LOCATION = "google/type/fraction.proto";
  public static final String INTERVAL_LOCATION = "google/type/interval.proto";
  public static final String LATLNG_LOCATION = "google/type/latlng.proto";
  public static final String MONEY_LOCATION = "google/type/money.proto";
  public static final String MONTH_LOCATION = "google/type/month.proto";
  public static final String PHONE_NUMBER_LOCATION = "google/type/phone_number.proto";
  public static final String POSTAL_ADDRESS_LOCATION = "google/type/postal_address.proto";
  public static final String QUATERNION_LOCATION = "google/type/quaternion.proto";
  public static final String TIME_OF_DAY_LOCATION = "google/type/timeofday.proto";
  public static final String ANY_LOCATION = "google/protobuf/any.proto";
  public static final String API_LOCATION = "google/protobuf/api.proto";
  public static final String DESCRIPTOR_LOCATION = "google/protobuf/descriptor.proto";
  public static final String DURATION_LOCATION = "google/protobuf/duration.proto";
  public static final String EMPTY_LOCATION = "google/protobuf/empty.proto";
  public static final String FIELD_MASK_LOCATION = "google/protobuf/field_mask.proto";
  public static final String SOURCE_CONTEXT_LOCATION = "google/protobuf/source_context.proto";
  public static final String STRUCT_LOCATION = "google/protobuf/struct.proto";
  public static final String TIMESTAMP_LOCATION = "google/protobuf/timestamp.proto";
  public static final String TYPE_LOCATION = "google/protobuf/type.proto";
  public static final String WRAPPER_LOCATION = "google/protobuf/wrappers.proto";

  private static final ProtoFileElement CFLT_META_SCHEMA =
      toProtoFile(MetaProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement CFLT_DECIMAL_SCHEMA =
      toProtoFile(DecimalProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement CALENDAR_PERIOD_SCHEMA =
      toProtoFile(CalendarPeriodProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement COLOR_SCHEMA =
      toProtoFile(ColorProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement DATE_SCHEMA =
      toProtoFile(DateProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement DATETIME_SCHEMA =
      toProtoFile(DateTimeProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement DAY_OF_WEEK_SCHEMA =
      toProtoFile(DayOfWeekProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement DECIMAL_SCHEMA =
      toProtoFile(com.google.type.DecimalProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement EXPR_SCHEMA =
      toProtoFile(ExprProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement FRACTION_SCHEMA =
      toProtoFile(FractionProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement INTERVAL_SCHEMA =
      toProtoFile(IntervalProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement LATLNG_SCHEMA =
      toProtoFile(LatLngProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement MONEY_SCHEMA =
      toProtoFile(MoneyProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement MONTH_SCHEMA =
      toProtoFile(MonthProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement PHONE_NUMBER_SCHEMA =
      toProtoFile(PhoneNumberProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement POSTAL_ADDRESS_SCHEMA =
      toProtoFile(PostalAddressProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement QUATERNION_SCHEMA =
      toProtoFile(QuaternionProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement TIME_OF_DAY_SCHEMA =
      toProtoFile(TimeOfDayProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement ANY_SCHEMA =
      toProtoFile(AnyProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement API_SCHEMA =
      toProtoFile(ApiProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement DESCRIPTOR_SCHEMA =
      toProtoFile(DescriptorProtos.getDescriptor().toProto()) ;
  private static final ProtoFileElement DURATION_SCHEMA =
      toProtoFile(DurationProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement EMPTY_SCHEMA =
      toProtoFile(EmptyProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement FIELD_MASK_SCHEMA =
      toProtoFile(FieldMaskProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement SOURCE_CONTEXT_SCHEMA =
      toProtoFile(SourceContextProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement STRUCT_SCHEMA =
      toProtoFile(StructProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement TIMESTAMP_SCHEMA =
      toProtoFile(TimestampProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement TYPE_SCHEMA =
      toProtoFile(TypeProto.getDescriptor().toProto()) ;
  private static final ProtoFileElement WRAPPER_SCHEMA =
      toProtoFile(WrappersProto.getDescriptor().toProto()) ;

  private static final HashMap<String, ProtoFileElement> KNOWN_DEPENDENCIES;
  
  static {
    KNOWN_DEPENDENCIES = new HashMap<>();
    KNOWN_DEPENDENCIES.put(CFLT_META_LOCATION, CFLT_META_SCHEMA);
    KNOWN_DEPENDENCIES.put(CFLT_DECIMAL_LOCATION, CFLT_DECIMAL_SCHEMA);
    KNOWN_DEPENDENCIES.put(CALENDAR_PERIOD_LOCATION, CALENDAR_PERIOD_SCHEMA);
    KNOWN_DEPENDENCIES.put(COLOR_LOCATION, COLOR_SCHEMA);
    KNOWN_DEPENDENCIES.put(DATE_LOCATION, DATE_SCHEMA);
    KNOWN_DEPENDENCIES.put(DATETIME_LOCATION, DATETIME_SCHEMA);
    KNOWN_DEPENDENCIES.put(DAY_OF_WEEK_LOCATION, DAY_OF_WEEK_SCHEMA);
    KNOWN_DEPENDENCIES.put(DECIMAL_LOCATION, DECIMAL_SCHEMA);
    KNOWN_DEPENDENCIES.put(EXPR_LOCATION, EXPR_SCHEMA);
    KNOWN_DEPENDENCIES.put(FRACTION_LOCATION, FRACTION_SCHEMA);
    KNOWN_DEPENDENCIES.put(INTERVAL_LOCATION, INTERVAL_SCHEMA);
    KNOWN_DEPENDENCIES.put(LATLNG_LOCATION, LATLNG_SCHEMA);
    KNOWN_DEPENDENCIES.put(MONEY_LOCATION, MONEY_SCHEMA);
    KNOWN_DEPENDENCIES.put(MONTH_LOCATION, MONTH_SCHEMA);
    KNOWN_DEPENDENCIES.put(PHONE_NUMBER_LOCATION, PHONE_NUMBER_SCHEMA);
    KNOWN_DEPENDENCIES.put(POSTAL_ADDRESS_LOCATION, POSTAL_ADDRESS_SCHEMA);
    KNOWN_DEPENDENCIES.put(QUATERNION_LOCATION, QUATERNION_SCHEMA);
    KNOWN_DEPENDENCIES.put(TIME_OF_DAY_LOCATION, TIME_OF_DAY_SCHEMA);
    KNOWN_DEPENDENCIES.put(ANY_LOCATION, ANY_SCHEMA);
    KNOWN_DEPENDENCIES.put(API_LOCATION, API_SCHEMA);
    KNOWN_DEPENDENCIES.put(DESCRIPTOR_LOCATION, DESCRIPTOR_SCHEMA);
    KNOWN_DEPENDENCIES.put(DURATION_LOCATION, DURATION_SCHEMA);
    KNOWN_DEPENDENCIES.put(EMPTY_LOCATION, EMPTY_SCHEMA);
    KNOWN_DEPENDENCIES.put(FIELD_MASK_LOCATION, FIELD_MASK_SCHEMA);
    KNOWN_DEPENDENCIES.put(SOURCE_CONTEXT_LOCATION, SOURCE_CONTEXT_SCHEMA);
    KNOWN_DEPENDENCIES.put(STRUCT_LOCATION, STRUCT_SCHEMA);
    KNOWN_DEPENDENCIES.put(TIMESTAMP_LOCATION, TIMESTAMP_SCHEMA);
    KNOWN_DEPENDENCIES.put(TYPE_LOCATION, TYPE_SCHEMA);
    KNOWN_DEPENDENCIES.put(WRAPPER_LOCATION, WRAPPER_SCHEMA);
  }

  public static Set<String> knownTypes() {
    return KNOWN_DEPENDENCIES.keySet();
  }

  private final ProtoFileElement schemaObj;

  private final Integer version;

  private final String name;

  private final List<SchemaReference> references;

  private final Map<String, ProtoFileElement> dependencies;

  private transient String canonicalString;

  private transient DynamicSchema dynamicSchema;

  private transient Descriptor descriptor;

  private transient int hashCode = NO_HASHCODE;

  private static final int NO_HASHCODE = Integer.MIN_VALUE;

  private static final Base64.Encoder base64Encoder = Base64.getEncoder();

  private static final Base64.Decoder base64Decoder = Base64.getDecoder();

  public ProtobufSchema(String schemaString) {
    this(schemaString, Collections.emptyList(), Collections.emptyMap(), null, null);
  }

  public ProtobufSchema(
      String schemaString,
      List<SchemaReference> references,
      Map<String, String> resolvedReferences,
      Integer version,
      String name
  ) {
    try {
      this.schemaObj = toProtoFile(schemaString);
      this.version = version;
      this.name = name;
      this.references = Collections.unmodifiableList(references);
      this.dependencies = Collections.unmodifiableMap(resolvedReferences.entrySet()
          .stream()
          .collect(Collectors.toMap(
              Map.Entry::getKey,
              e -> toProtoFile(e.getValue())
          )));
    } catch (IllegalStateException e) {
      log.error("Could not parse Protobuf schema " + schemaString
          + " with references " + references, e);
      throw e;
    }
  }

  public ProtobufSchema(
      ProtoFileElement protoFileElement,
      List<SchemaReference> references,
      Map<String, ProtoFileElement> dependencies
  ) {
    this.schemaObj = protoFileElement;
    this.version = null;
    this.name = null;
    this.references = Collections.unmodifiableList(references);
    this.dependencies = Collections.unmodifiableMap(dependencies);
  }

  public ProtobufSchema(Descriptor descriptor) {
    this(descriptor, Collections.emptyList());
  }

  public ProtobufSchema(Descriptor descriptor, List<SchemaReference> references) {
    Map<String, ProtoFileElement> dependencies = new HashMap<>();
    this.schemaObj = toProtoFile(descriptor.getFile(), dependencies);
    this.version = null;
    this.name = descriptor.getFullName();
    this.references = Collections.unmodifiableList(references);
    this.dependencies = Collections.unmodifiableMap(dependencies);
    this.descriptor = descriptor;
  }

  private ProtobufSchema(
      ProtoFileElement schemaObj,
      Integer version,
      String name,
      List<SchemaReference> references,
      Map<String, ProtoFileElement> dependencies,
      String canonicalString,
      DynamicSchema dynamicSchema,
      Descriptor descriptor
  ) {
    this.schemaObj = schemaObj;
    this.version = version;
    this.name = name;
    this.references = references;
    this.dependencies = dependencies;
    this.canonicalString = canonicalString;
    this.dynamicSchema = dynamicSchema;
    this.descriptor = descriptor;
  }

  public ProtobufSchema copy() {
    return new ProtobufSchema(
        this.schemaObj,
        this.version,
        this.name,
        this.references,
        this.dependencies,
        this.canonicalString,
        this.dynamicSchema,
        this.descriptor
    );
  }

  public ProtobufSchema copy(Integer version) {
    return new ProtobufSchema(
        this.schemaObj,
        version,
        this.name,
        this.references,
        this.dependencies,
        this.canonicalString,
        this.dynamicSchema,
        this.descriptor
    );
  }

  public ProtobufSchema copy(String name) {
    return new ProtobufSchema(
        this.schemaObj,
        this.version,
        name,
        this.references,
        this.dependencies,
        this.canonicalString,
        this.dynamicSchema,
        // reset descriptor if names not equal
        Objects.equals(this.name, name) ? this.descriptor : null
    );
  }

  public ProtobufSchema copy(List<SchemaReference> references) {
    return new ProtobufSchema(
        this.schemaObj,
        this.version,
        this.name,
        references,
        this.dependencies,
        this.canonicalString,
        this.dynamicSchema,
        this.descriptor
    );
  }

  private ProtoFileElement toProtoFile(String schema) {
    try {
      return ProtoParser.Companion.parse(DEFAULT_LOCATION, schema);
    } catch (Exception e) {
      try {
        // Attempt to parse binary FileDescriptorProto
        byte[] bytes = base64Decoder.decode(schema);
        return toProtoFile(FileDescriptorProto.parseFrom(bytes));
      } catch (Exception pe) {
        throw new IllegalArgumentException("Could not parse Protobuf", e);
      }
    }
  }

  private static ProtoFileElement toProtoFile(
      FileDescriptor file, Map<String, ProtoFileElement> dependencies
  ) {
    for (FileDescriptor dependency : file.getDependencies()) {
      String depName = dependency.getName();
      dependencies.put(depName, toProtoFile(dependency, dependencies));
    }
    return toProtoFile(file.toProto());
  }

  private static ProtoFileElement toProtoFile(FileDescriptorProto file) {
    String packageName = file.getPackage();
    // Don't set empty package name
    if ("".equals(packageName)) {
      packageName = null;
    }
    Syntax syntax = null;
    switch (file.getSyntax()) {
      case PROTO2:
        syntax = Syntax.PROTO_2;
        break;
      case PROTO3:
        syntax = Syntax.PROTO_3;
        break;
      default:
        break;
    }
    ImmutableList.Builder<TypeElement> types = ImmutableList.builder();
    for (DescriptorProto md : file.getMessageTypeList()) {
      MessageElement message = toMessage(file, md);
      types.add(message);
    }
    for (EnumDescriptorProto ed : file.getEnumTypeList()) {
      EnumElement enumer = toEnum(ed);
      types.add(enumer);
    }
    ImmutableList.Builder<ServiceElement> services = ImmutableList.builder();
    for (ServiceDescriptorProto sd : file.getServiceList()) {
      ServiceElement service = toService(sd);
      services.add(service);
    }
    ImmutableList.Builder<String> imports = ImmutableList.builder();
    ImmutableList.Builder<String> publicImports = ImmutableList.builder();
    List<String> dependencyList = file.getDependencyList();
    Set<Integer> publicDependencyList = new HashSet<>(file.getPublicDependencyList());
    for (int i = 0; i < dependencyList.size(); i++) {
      String depName = dependencyList.get(i);
      if (publicDependencyList.contains(i)) {
        publicImports.add(depName);
      } else {
        imports.add(depName);
      }
    }
    ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
    if (file.getOptions().hasJavaPackage()) {
      options.add(new OptionElement(
          JAVA_PACKAGE, Kind.STRING, file.getOptions().getJavaPackage(), false));
    }
    if (file.getOptions().hasJavaOuterClassname()) {
      options.add(new OptionElement(
          JAVA_OUTER_CLASSNAME, Kind.STRING, file.getOptions().getJavaOuterClassname(), false));
    }
    if (file.getOptions().hasJavaMultipleFiles()) {
      options.add(new OptionElement(
          JAVA_MULTIPLE_FILES, Kind.BOOLEAN, file.getOptions().getJavaMultipleFiles(), false));
    }
    if (file.getOptions().hasJavaStringCheckUtf8()) {
      options.add(new OptionElement(
          JAVA_STRING_CHECK_UTF8, Kind.BOOLEAN, file.getOptions().getJavaStringCheckUtf8(), false));
    }
    if (file.getOptions().hasOptimizeFor()) {
      options.add(new OptionElement(
          OPTIMIZE_FOR, Kind.ENUM, file.getOptions().getOptimizeFor(), false));
    }
    if (file.getOptions().hasGoPackage()) {
      options.add(new OptionElement(
          GO_PACKAGE, Kind.STRING, file.getOptions().getGoPackage(), false));
    }
    if (file.getOptions().hasCcGenericServices()) {
      options.add(new OptionElement(
          CC_GENERIC_SERVICES, Kind.BOOLEAN, file.getOptions().getCcGenericServices(), false));
    }
    if (file.getOptions().hasJavaGenericServices()) {
      options.add(new OptionElement(
          JAVA_GENERIC_SERVICES, Kind.BOOLEAN, file.getOptions().getJavaGenericServices(), false));
    }
    if (file.getOptions().hasPyGenericServices()) {
      options.add(new OptionElement(
          PY_GENERIC_SERVICES, Kind.BOOLEAN, file.getOptions().getPyGenericServices(), false));
    }
    if (file.getOptions().hasPhpGenericServices()) {
      options.add(new OptionElement(
          PHP_GENERIC_SERVICES, Kind.BOOLEAN, file.getOptions().getPhpGenericServices(), false));
    }
    if (file.getOptions().hasDeprecated()) {
      options.add(new OptionElement(
          DEPRECATED, Kind.BOOLEAN, file.getOptions().getDeprecated(), false));
    }
    if (file.getOptions().hasCcEnableArenas()) {
      options.add(new OptionElement(
          CC_ENABLE_ARENAS, Kind.BOOLEAN, file.getOptions().getCcEnableArenas(), false));
    }
    if (file.getOptions().hasObjcClassPrefix()) {
      options.add(new OptionElement(
          OBJC_CLASS_PREFIX, Kind.STRING, file.getOptions().getObjcClassPrefix(), false));
    }
    if (file.getOptions().hasCsharpNamespace()) {
      options.add(new OptionElement(
          CSHARP_NAMESPACE, Kind.STRING, file.getOptions().getCsharpNamespace(), false));
    }
    if (file.getOptions().hasSwiftPrefix()) {
      options.add(new OptionElement(
          SWIFT_PREFIX, Kind.STRING, file.getOptions().getSwiftPrefix(), false));
    }
    if (file.getOptions().hasPhpClassPrefix()) {
      options.add(new OptionElement(
          PHP_CLASS_PREFIX, Kind.STRING, file.getOptions().getPhpClassPrefix(), false));
    }
    if (file.getOptions().hasPhpNamespace()) {
      options.add(new OptionElement(
          PHP_NAMESPACE, Kind.STRING, file.getOptions().getPhpNamespace(), false));
    }
    if (file.getOptions().hasPhpMetadataNamespace()) {
      options.add(new OptionElement(
          PHP_METADATA_NAMESPACE, Kind.STRING, file.getOptions().getPhpMetadataNamespace(), false));
    }
    if (file.getOptions().hasRubyPackage()) {
      options.add(new OptionElement(
          RUBY_PACKAGE, Kind.STRING, file.getOptions().getRubyPackage(), false));
    }
    if (file.getOptions().hasExtension(MetaProto.fileMeta)) {
      Meta meta = file.getOptions().getExtension(MetaProto.fileMeta);
      OptionElement option = toOption(CONFLUENT_FILE_META, meta);
      if (option != null) {
        options.add(option);
      }
    }
    // NOTE: skip extensions
    return new ProtoFileElement(DEFAULT_LOCATION,
        packageName,
        syntax,
        imports.build(),
        publicImports.build(),
        types.build(),
        services.build(),
        Collections.emptyList(),
        options.build()
    );
  }

  private static MessageElement toMessage(FileDescriptorProto file, DescriptorProto descriptor) {
    String name = descriptor.getName();
    log.trace("*** msg name: {}", name);
    ImmutableList.Builder<FieldElement> fields = ImmutableList.builder();
    ImmutableList.Builder<TypeElement> nested = ImmutableList.builder();
    ImmutableList.Builder<ReservedElement> reserved = ImmutableList.builder();
    LinkedHashMap<String, ImmutableList.Builder<FieldElement>> oneofsMap = new LinkedHashMap<>();
    for (OneofDescriptorProto od : descriptor.getOneofDeclList()) {
      oneofsMap.put(od.getName(), ImmutableList.builder());
    }
    List<Map.Entry<String, ImmutableList.Builder<FieldElement>>> oneofs =
        new ArrayList<>(oneofsMap.entrySet());
    for (FieldDescriptorProto fd : descriptor.getFieldList()) {
      if (fd.hasOneofIndex() && !fd.getProto3Optional()) {
        FieldElement field = toField(file, fd, true);
        oneofs.get(fd.getOneofIndex()).getValue().add(field);
      } else {
        FieldElement field = toField(file, fd, false);
        fields.add(field);
      }
    }
    for (DescriptorProto nestedDesc : descriptor.getNestedTypeList()) {
      MessageElement nestedMessage = toMessage(file, nestedDesc);
      nested.add(nestedMessage);
    }
    for (EnumDescriptorProto nestedDesc : descriptor.getEnumTypeList()) {
      EnumElement nestedEnum = toEnum(nestedDesc);
      nested.add(nestedEnum);
    }
    for (ReservedRange range : descriptor.getReservedRangeList()) {
      ReservedElement reservedElem = toReserved(range);
      reserved.add(reservedElem);
    }
    for (String reservedName : descriptor.getReservedNameList()) {
      ReservedElement reservedElem = new ReservedElement(
          DEFAULT_LOCATION,
          "",
          Collections.singletonList(reservedName)
      );
      reserved.add(reservedElem);
    }
    ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
    if (descriptor.getOptions().hasNoStandardDescriptorAccessor()) {
      OptionElement option = new OptionElement(
          NO_STANDARD_DESCRIPTOR_ACCESSOR, Kind.BOOLEAN,
          descriptor.getOptions().getNoStandardDescriptorAccessor(), false
      );
      options.add(option);
    }
    if (descriptor.getOptions().hasDeprecated()) {
      OptionElement option = new OptionElement(
          DEPRECATED, Kind.BOOLEAN,
          descriptor.getOptions().getDeprecated(), false
      );
      options.add(option);
    }
    if (descriptor.getOptions().hasMapEntry()) {
      OptionElement option = new OptionElement(
          MAP_ENTRY, Kind.BOOLEAN,
          descriptor.getOptions().getMapEntry(), false
      );
      options.add(option);
    }
    if (descriptor.getOptions().hasExtension(MetaProto.messageMeta)) {
      Meta meta = descriptor.getOptions().getExtension(MetaProto.messageMeta);
      OptionElement option = toOption(CONFLUENT_MESSAGE_META, meta);
      if (option != null) {
        options.add(option);
      }
    }
    // NOTE: skip extensions, groups
    return new MessageElement(DEFAULT_LOCATION,
        name,
        "",
        nested.build(),
        options.build(),
        reserved.build(),
        fields.build(),
        oneofs.stream()
            .map(e -> toOneof(e.getKey(), e.getValue()))
            .filter(e -> !e.getFields().isEmpty())
            .collect(Collectors.toList()),
        Collections.emptyList(),
        Collections.emptyList()
    );
  }

  private static OptionElement toOption(String name, Meta meta) {
    Map<String, Object> map = new HashMap<>();
    String doc = meta.getDoc();
    if (doc != null && !doc.isEmpty()) {
      map.put(DOC_FIELD, doc);
    }
    Map<String, String> params = meta.getParamsMap();
    if (params != null && !params.isEmpty()) {
      List<Map<String, String>> keyValues = new ArrayList<>();
      for (Map.Entry<String, String> entry : params.entrySet()) {
        Map<String, String> keyValue = new HashMap<>();
        keyValue.put(KEY_FIELD, entry.getKey());
        keyValue.put(VALUE_FIELD, entry.getValue());
        keyValues.add(keyValue);
      }
      map.put(PARAMS_FIELD, keyValues);
    }
    return map.isEmpty() ? null : new OptionElement(name, Kind.MAP, map, true);
  }

  private static ReservedElement toReserved(ReservedRange range) {
    List<Object> values = new ArrayList<>();
    int start = range.getStart();
    int end = range.getEnd();
    values.add(start == end - 1 ? start : new IntRange(start, end - 1));
    return new ReservedElement(DEFAULT_LOCATION, "", values);
  }

  private static OneOfElement toOneof(String name, ImmutableList.Builder<FieldElement> fields) {
    log.trace("*** oneof name: {}", name);
    // NOTE: skip groups
    return new OneOfElement(name, "", fields.build(),
        Collections.emptyList(), Collections.emptyList());
  }

  private static EnumElement toEnum(EnumDescriptorProto ed) {
    String name = ed.getName();
    log.trace("*** enum name: {}", name);
    ImmutableList.Builder<EnumConstantElement> constants = ImmutableList.builder();
    for (EnumValueDescriptorProto ev : ed.getValueList()) {
      ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
      if (ev.getOptions().hasDeprecated()) {
        OptionElement option = new OptionElement(
            DEPRECATED, Kind.BOOLEAN,
            ev.getOptions().getDeprecated(), false
        );
        options.add(option);
      }
      if (ev.getOptions().hasExtension(MetaProto.enumValueMeta)) {
        Meta meta = ev.getOptions().getExtension(MetaProto.enumValueMeta);
        OptionElement option = toOption(CONFLUENT_ENUM_VALUE_META, meta);
        if (option != null) {
          options.add(option);
        }
      }
      constants.add(new EnumConstantElement(
          DEFAULT_LOCATION,
          ev.getName(),
          ev.getNumber(),
          "",
          options.build()
      ));
    }
    ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
    if (ed.getOptions().hasAllowAlias()) {
      OptionElement option = new OptionElement(
          ALLOW_ALIAS, Kind.BOOLEAN,
          ed.getOptions().getAllowAlias(), false
      );
      options.add(option);
    }
    if (ed.getOptions().hasDeprecated()) {
      OptionElement option = new OptionElement(
          DEPRECATED, Kind.BOOLEAN,
          ed.getOptions().getDeprecated(), false
      );
      options.add(option);
    }
    if (ed.getOptions().hasExtension(MetaProto.enumMeta)) {
      Meta meta = ed.getOptions().getExtension(MetaProto.enumMeta);
      OptionElement option = toOption(CONFLUENT_ENUM_META, meta);
      if (option != null) {
        options.add(option);
      }
    }
    return new EnumElement(DEFAULT_LOCATION, name, "", options.build(), constants.build());
  }

  private static ServiceElement toService(ServiceDescriptorProto sd) {
    String name = sd.getName();
    log.trace("*** service name: {}", name);
    ImmutableList.Builder<RpcElement> methods = ImmutableList.builder();
    for (MethodDescriptorProto method : sd.getMethodList()) {
      ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
      if (method.getOptions().hasDeprecated()) {
        OptionElement option = new OptionElement(
            DEPRECATED, Kind.BOOLEAN,
            method.getOptions().getDeprecated(), false
        );
        options.add(option);
      }
      if (method.getOptions().hasIdempotencyLevel()) {
        OptionElement option = new OptionElement(
            IDEMPOTENCY_LEVEL, Kind.ENUM,
            method.getOptions().getIdempotencyLevel(), false
        );
        options.add(option);
      }
      methods.add(new RpcElement(
          DEFAULT_LOCATION,
          method.getName(),
          "",
          method.getInputType(),
          method.getOutputType(),
          method.getClientStreaming(),
          method.getServerStreaming(),
          options.build()
      ));
    }
    ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
    if (sd.getOptions().hasDeprecated()) {
      OptionElement option = new OptionElement(
          DEPRECATED, Kind.BOOLEAN,
          sd.getOptions().getDeprecated(), false
      );
      options.add(option);
    }
    return new ServiceElement(DEFAULT_LOCATION, name, "", methods.build(), options.build());
  }

  private static FieldElement toField(
      FileDescriptorProto file, FieldDescriptorProto fd, boolean inOneof) {
    String name = fd.getName();
    log.trace("*** field name: {}", name);
    ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
    if (fd.getOptions().hasCtype()) {
      OptionElement option = new OptionElement(CTYPE, Kind.ENUM, fd.getOptions().getCtype(), false);
      options.add(option);
    }
    if (fd.getOptions().hasPacked()) {
      OptionElement option =
          new OptionElement(PACKED, Kind.BOOLEAN, fd.getOptions().getPacked(), false);
      options.add(option);
    }
    if (fd.getOptions().hasJstype()) {
      OptionElement option =
          new OptionElement(JSTYPE, Kind.ENUM, fd.getOptions().getJstype(), false);
      options.add(option);
    }
    if (fd.getOptions().hasDeprecated()) {
      OptionElement option =
          new OptionElement(DEPRECATED, Kind.BOOLEAN, fd.getOptions().getDeprecated(), false);
      options.add(option);
    }
    if (fd.getOptions().hasExtension(MetaProto.fieldMeta)) {
      Meta meta = fd.getOptions().getExtension(MetaProto.fieldMeta);
      OptionElement option = toOption(CONFLUENT_FIELD_META, meta);
      if (option != null) {
        options.add(option);
      }
    }
    String jsonName = fd.hasJsonName() ? fd.getJsonName() : null;
    String defaultValue = !PROTO3.equals(file.getSyntax()) && fd.hasDefaultValue()
                          ? fd.getDefaultValue()
                          : null;
    return new FieldElement(DEFAULT_LOCATION,
        inOneof ? null : label(file, fd),
        dataType(fd),
        name,
        defaultValue,
        jsonName,
        fd.getNumber(),
        "",
        options.build()
    );
  }

  private static Field.Label label(FileDescriptorProto file, FieldDescriptorProto fd) {
    if (fd.getProto3Optional()) {
      return Field.Label.OPTIONAL;
    }
    boolean isProto3 = file.getSyntax().equals(PROTO3);
    switch (fd.getLabel()) {
      case LABEL_REQUIRED:
        return isProto3 ? null : Field.Label.REQUIRED;
      case LABEL_OPTIONAL:
        return isProto3 ? null : Field.Label.OPTIONAL;
      case LABEL_REPEATED:
        return Field.Label.REPEATED;
      default:
        throw new IllegalArgumentException("Unsupported label");
    }
  }

  private static String dataType(FieldDescriptorProto field) {
    if (field.hasTypeName()) {
      return field.getTypeName();
    } else {
      FieldDescriptorProto.Type type = field.getType();
      return FieldDescriptor.Type.valueOf(type).name().toLowerCase();
    }
  }

  public Descriptor toDescriptor() {
    if (schemaObj == null) {
      return null;
    }
    if (descriptor == null) {
      descriptor = toDescriptor(name());
    }
    return descriptor;
  }

  public Descriptor toDescriptor(String name) {
    return toDynamicSchema().getMessageDescriptor(name);
  }

  public DynamicMessage.Builder newMessageBuilder() {
    return newMessageBuilder(name());
  }

  public DynamicMessage.Builder newMessageBuilder(String name) {
    return toDynamicSchema().newMessageBuilder(name);
  }

  public Descriptors.EnumValueDescriptor getEnumValue(String enumTypeName, int enumNumber) {
    return toDynamicSchema().getEnumValue(enumTypeName, enumNumber);
  }

  private MessageElement firstMessage() {
    for (TypeElement typeElement : schemaObj.getTypes()) {
      if (typeElement instanceof MessageElement) {
        return (MessageElement) typeElement;
      }
    }
    return null;
  }

  private EnumElement firstEnum() {
    for (TypeElement typeElement : schemaObj.getTypes()) {
      if (typeElement instanceof EnumElement) {
        return (EnumElement) typeElement;
      }
    }
    return null;
  }

  public DynamicSchema toDynamicSchema() {
    return toDynamicSchema(DEFAULT_NAME);
  }

  public DynamicSchema toDynamicSchema(String name) {
    if (schemaObj == null) {
      return null;
    }
    if (dynamicSchema == null) {
      dynamicSchema = toDynamicSchema(name, schemaObj, dependenciesWithLogicalTypes());
    }
    return dynamicSchema;
  }

  private static DynamicSchema toDynamicSchema(
      String name, ProtoFileElement rootElem, Map<String, ProtoFileElement> dependencies
  ) {
    if (log.isTraceEnabled()) {
      log.trace("*** toDynamicSchema: {}", ProtobufSchemaUtils.toString(rootElem));
    }
    DynamicSchema.Builder schema = DynamicSchema.newBuilder();
    try {
      Syntax syntax = rootElem.getSyntax();
      if (syntax != null) {
        schema.setSyntax(syntax.toString());
      }
      if (rootElem.getPackageName() != null) {
        schema.setPackage(rootElem.getPackageName());
      }
      for (TypeElement typeElem : rootElem.getTypes()) {
        if (typeElem instanceof MessageElement) {
          MessageDefinition message = toDynamicMessage(syntax, (MessageElement) typeElem);
          schema.addMessageDefinition(message);
        } else if (typeElem instanceof EnumElement) {
          EnumDefinition enumer = toDynamicEnum((EnumElement) typeElem);
          schema.addEnumDefinition(enumer);
        }
      }
      for (ServiceElement serviceElement : rootElem.getServices()) {
        ServiceDefinition service = toDynamicService(serviceElement);
        schema.addServiceDefinition(service);
      }
      for (String ref : rootElem.getImports()) {
        ProtoFileElement dep = dependencies.get(ref);
        if (dep != null) {
          schema.addDependency(ref);
          schema.addSchema(toDynamicSchema(ref, dep, dependencies));
        }
      }
      for (String ref : rootElem.getPublicImports()) {
        ProtoFileElement dep = dependencies.get(ref);
        if (dep != null) {
          schema.addPublicDependency(ref);
          schema.addSchema(toDynamicSchema(ref, dep, dependencies));
        }
      }
      Map<String, OptionElement> options = rootElem.getOptions().stream()
          .collect(Collectors.toMap(OptionElement::getName, o -> o));
      OptionElement javaPackageName = options.get(JAVA_PACKAGE);
      if (javaPackageName != null) {
        schema.setJavaPackage(javaPackageName.getValue().toString());
      }
      OptionElement javaOuterClassname = options.get(JAVA_OUTER_CLASSNAME);
      if (javaOuterClassname != null) {
        schema.setJavaOuterClassname(javaOuterClassname.getValue().toString());
      }
      OptionElement javaMultipleFiles = options.get(JAVA_MULTIPLE_FILES);
      if (javaMultipleFiles != null) {
        schema.setJavaMultipleFiles(Boolean.parseBoolean(javaMultipleFiles.getValue().toString()));
      }
      OptionElement javaStringCheckUtf8 = options.get(JAVA_STRING_CHECK_UTF8);
      if (javaStringCheckUtf8 != null) {
        schema.setJavaStringCheckUtf8(
            Boolean.parseBoolean(javaStringCheckUtf8.getValue().toString()));
      }
      OptionElement optimizeFor = options.get(OPTIMIZE_FOR);
      if (optimizeFor != null) {
        schema.setOptimizeFor(OptimizeMode.valueOf(optimizeFor.getValue().toString()));
      }
      OptionElement goPackage = options.get(GO_PACKAGE);
      if (goPackage != null) {
        schema.setGoPackage(goPackage.getValue().toString());
      }
      OptionElement ccGenericServices = options.get(CC_GENERIC_SERVICES);
      if (ccGenericServices != null) {
        schema.setCcGenericServices(Boolean.parseBoolean(ccGenericServices.getValue().toString()));
      }
      OptionElement javaGenericServices = options.get(JAVA_GENERIC_SERVICES);
      if (javaGenericServices != null) {
        schema.setJavaGenericServices(
            Boolean.parseBoolean(javaGenericServices.getValue().toString()));
      }
      OptionElement pyGenericServices = options.get(PY_GENERIC_SERVICES);
      if (pyGenericServices != null) {
        schema.setPyGenericServices(Boolean.parseBoolean(pyGenericServices.getValue().toString()));
      }
      OptionElement phpGenericServices = options.get(PHP_GENERIC_SERVICES);
      if (phpGenericServices != null) {
        schema.setPhpGenericServices(
            Boolean.parseBoolean(phpGenericServices.getValue().toString()));
      }
      OptionElement isDeprecated = options.get(DEPRECATED);
      if (isDeprecated != null) {
        schema.setDeprecated(Boolean.parseBoolean(isDeprecated.getValue().toString()));
      }
      OptionElement ccEnableArenas = options.get(CC_ENABLE_ARENAS);
      if (ccEnableArenas != null) {
        schema.setCcEnableArenas(Boolean.parseBoolean(ccEnableArenas.getValue().toString()));
      }
      OptionElement objcClassPrefix = options.get(OBJC_CLASS_PREFIX);
      if (objcClassPrefix != null) {
        schema.setObjcClassPrefix(objcClassPrefix.getValue().toString());
      }
      OptionElement csharpNamespace = options.get(CSHARP_NAMESPACE);
      if (csharpNamespace != null) {
        schema.setCsharpNamespace(csharpNamespace.getValue().toString());
      }
      OptionElement swiftPrefix = options.get(SWIFT_PREFIX);
      if (swiftPrefix != null) {
        schema.setSwiftPrefix(swiftPrefix.getValue().toString());
      }
      OptionElement phpClassPrefix = options.get(PHP_CLASS_PREFIX);
      if (phpClassPrefix != null) {
        schema.setPhpClassPrefix(phpClassPrefix.getValue().toString());
      }
      OptionElement phpNamespace = options.get(PHP_NAMESPACE);
      if (phpNamespace != null) {
        schema.setPhpNamespace(phpNamespace.getValue().toString());
      }
      OptionElement phpMetadataNamespace = options.get(PHP_METADATA_NAMESPACE);
      if (phpMetadataNamespace != null) {
        schema.setPhpMetadataNamespace(phpMetadataNamespace.getValue().toString());
      }
      OptionElement rubyPackage = options.get(RUBY_PACKAGE);
      if (rubyPackage != null) {
        schema.setRubyPackage(rubyPackage.getValue().toString());
      }
      Optional<OptionElement> meta = findOption(CONFLUENT_FILE_META, rootElem.getOptions());
      String doc = findDoc(meta);
      Map<String, String> params = findParams(meta);
      schema.setMeta(doc, params);
      schema.setName(name);
      return schema.build();
    } catch (Descriptors.DescriptorValidationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static MessageDefinition toDynamicMessage(
      Syntax syntax,
      MessageElement messageElem
  ) {
    log.trace("*** message: {}", messageElem.getName());
    MessageDefinition.Builder message = MessageDefinition.newBuilder(messageElem.getName());
    for (TypeElement type : messageElem.getNestedTypes()) {
      if (type instanceof MessageElement) {
        message.addMessageDefinition(toDynamicMessage(syntax, (MessageElement) type));
      } else if (type instanceof EnumElement) {
        message.addEnumDefinition(toDynamicEnum((EnumElement) type));
      }
    }
    Set<String> added = new HashSet<>();
    for (OneOfElement oneof : messageElem.getOneOfs()) {
      MessageDefinition.OneofBuilder oneofBuilder = message.addOneof(oneof.getName());
      for (FieldElement field : oneof.getFields()) {
        String defaultVal = field.getDefaultValue();
        String jsonName = field.getJsonName();
        CType ctype = findOption(CTYPE, field.getOptions())
            .map(o -> CType.valueOf(o.getValue().toString())).orElse(null);
        JSType jstype = findOption(JSTYPE, field.getOptions())
            .map(o -> JSType.valueOf(o.getValue().toString())).orElse(null);
        Boolean isDeprecated = findOption(DEPRECATED, field.getOptions())
            .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
        Optional<OptionElement> meta = findOption(CONFLUENT_FIELD_META, field.getOptions());
        String doc = findDoc(meta);
        Map<String, String> params = findParams(meta);
        oneofBuilder.addField(
            false,
            field.getType(),
            field.getName(),
            field.getTag(),
            defaultVal,
            jsonName,
            doc,
            params,
            ctype,
            jstype,
            isDeprecated
        );
        added.add(field.getName());
      }
    }
    // Process fields after messages so that any newly created map entry messages are at the end
    for (FieldElement field : messageElem.getFields()) {
      if (added.contains(field.getName())) {
        continue;
      }
      Field.Label fieldLabel = field.getLabel();
      String label = fieldLabel != null ? fieldLabel.toString().toLowerCase() : null;
      boolean isProto3Optional = "optional".equals(label) && syntax == Syntax.PROTO_3;
      String fieldType = field.getType();
      String defaultVal = field.getDefaultValue();
      String jsonName = field.getJsonName();
      CType ctype = findOption(CTYPE, field.getOptions())
          .map(o -> CType.valueOf(o.getValue().toString())).orElse(null);
      Boolean isPacked = findOption(PACKED, field.getOptions())
          .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
      JSType jstype = findOption(JSTYPE, field.getOptions())
          .map(o -> JSType.valueOf(o.getValue().toString())).orElse(null);
      Boolean isDeprecated = findOption(DEPRECATED, field.getOptions())
          .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
      Optional<OptionElement> meta = findOption(CONFLUENT_FIELD_META, field.getOptions());
      String doc = findDoc(meta);
      Map<String, String> params = findParams(meta);
      ProtoType protoType = ProtoType.get(fieldType);
      ProtoType keyType = protoType.getKeyType();
      ProtoType valueType = protoType.getValueType();
      // Map fields are only permitted in messages
      if (protoType.isMap() && keyType != null && valueType != null) {
        label = "repeated";
        fieldType = toMapEntry(field.getName());
        MessageDefinition.Builder mapMessage = MessageDefinition.newBuilder(fieldType);
        mapMessage.setMapEntry(true);
        mapMessage.addField(null, keyType.toString(), KEY_FIELD, 1, null, null, null);
        mapMessage.addField(null, valueType.toString(), VALUE_FIELD, 2, null, null, null);
        message.addMessageDefinition(mapMessage.build());
      }
      if (isProto3Optional) {
        // Add synthetic oneof after real oneofs
        MessageDefinition.OneofBuilder oneofBuilder = message.addOneof("_" + field.getName());
        oneofBuilder.addField(
            true,
            fieldType,
            field.getName(),
            field.getTag(),
            defaultVal,
            jsonName,
            doc,
            params,
            ctype,
            jstype,
            isDeprecated
        );
      } else {
        message.addField(
            label,
            fieldType,
            field.getName(),
            field.getTag(),
            defaultVal,
            jsonName,
            doc,
            params,
            ctype,
            isPacked,
            jstype,
            isDeprecated
        );
      }
    }
    for (ReservedElement reserved : messageElem.getReserveds()) {
      for (Object elem : reserved.getValues()) {
        if (elem instanceof String) {
          message.addReservedName((String) elem);
        } else if (elem instanceof Integer) {
          int tag = (Integer) elem;
          message.addReservedRange(tag, tag + 1);
        } else if (elem instanceof IntRange) {
          IntRange range = (IntRange) elem;
          message.addReservedRange(range.getStart(), range.getEndInclusive() + 1);
        } else {
          throw new IllegalStateException("Unsupported reserved type: " + elem.getClass()
              .getName());
        }
      }
    }
    Boolean noStandardDescriptorAccessor =
        findOption(NO_STANDARD_DESCRIPTOR_ACCESSOR, messageElem.getOptions())
            .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
    if (noStandardDescriptorAccessor != null) {
      message.setNoStandardDescriptorAccessor(noStandardDescriptorAccessor);
    }
    Boolean isDeprecated = findOption(DEPRECATED, messageElem.getOptions())
        .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
    if (isDeprecated != null) {
      message.setDeprecated(isDeprecated);
    }
    Boolean isMapEntry = findOption(MAP_ENTRY, messageElem.getOptions())
        .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
    if (isMapEntry != null) {
      message.setMapEntry(isMapEntry);
    }
    Optional<OptionElement> meta = findOption(CONFLUENT_MESSAGE_META, messageElem.getOptions());
    String doc = findDoc(meta);
    Map<String, String> params = findParams(meta);
    message.setMeta(doc, params);
    return message.build();
  }

  public static Optional<OptionElement> findOption(String name, List<OptionElement> options) {
    return options.stream().filter(o -> o.getName().equals(name)).findFirst();
  }

  public static String findDoc(Optional<OptionElement> meta) {
    return (String) findMeta(meta, DOC_FIELD);
  }

  public static Map<String, String> findParams(Optional<OptionElement> meta) {
    List<Map<String, String>> keyValues = (List<Map<String, String>>) findMeta(meta, PARAMS_FIELD);
    if (keyValues == null) {
      return null;
    }
    Map<String, String> params = new HashMap<>();
    for (Map<String, String> keyValue : keyValues) {
      String key = keyValue.get(KEY_FIELD);
      String value = keyValue.get(VALUE_FIELD);
      params.put(key, value);
    }
    return params;
  }

  public static Object findMeta(Optional<OptionElement> meta, String name) {
    if (!meta.isPresent()) {
      return null;
    }
    OptionElement options = meta.get();
    switch (options.getKind()) {
      case OPTION:
        OptionElement option = (OptionElement) options.getValue();
        if (option.getName().equals(name)) {
          return option.getValue();
        } else {
          return null;
        }
      case MAP:
        Map<String, ?> map = (Map<String, ?>) options.getValue();
        return map.get(name);
      default:
        throw new IllegalStateException("Unexpected custom option kind " + options.getKind());
    }
  }

  private static EnumDefinition toDynamicEnum(EnumElement enumElem) {
    Boolean allowAlias = findOption(ALLOW_ALIAS, enumElem.getOptions())
        .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
    Boolean isDeprecated = findOption(DEPRECATED, enumElem.getOptions())
        .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
    EnumDefinition.Builder enumer =
        EnumDefinition.newBuilder(enumElem.getName(), allowAlias, isDeprecated);
    for (EnumConstantElement constant : enumElem.getConstants()) {
      Boolean isConstDeprecated = findOption(DEPRECATED, constant.getOptions())
          .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
      Optional<OptionElement> meta = findOption(CONFLUENT_ENUM_VALUE_META, constant.getOptions());
      String doc = findDoc(meta);
      Map<String, String> params = findParams(meta);
      enumer.addValue(constant.getName(), constant.getTag(), doc, params, isConstDeprecated);
    }
    Optional<OptionElement> meta = findOption(CONFLUENT_ENUM_META, enumElem.getOptions());
    String doc = findDoc(meta);
    Map<String, String> params = findParams(meta);
    enumer.setMeta(doc, params);
    return enumer.build();
  }

  private static ServiceDefinition toDynamicService(ServiceElement serviceElement) {
    ServiceDefinition.Builder service =
        ServiceDefinition.newBuilder(serviceElement.getName());
    Boolean isDeprecated = findOption(DEPRECATED, serviceElement.getOptions())
        .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
    if (isDeprecated != null) {
      service.setDeprecated(isDeprecated);
    }
    for (RpcElement method : serviceElement.getRpcs()) {
      Boolean isMethodDeprecated = findOption(DEPRECATED, method.getOptions())
          .map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
      IdempotencyLevel idempotencyLevel = findOption(IDEMPOTENCY_LEVEL, method.getOptions())
          .map(o -> IdempotencyLevel.valueOf(o.getValue().toString())).orElse(null);
      service.addMethod(method.getName(), method.getRequestType(), method.getResponseType(),
          method.getRequestStreaming(), method.getResponseStreaming(),
          isMethodDeprecated, idempotencyLevel);
    }
    return service.build();
  }

  @Override
  public ProtoFileElement rawSchema() {
    return schemaObj;
  }

  @Override
  public String schemaType() {
    return TYPE;
  }

  @Override
  public String name() {
    if (name != null) {
      return name;
    }
    TypeElement typeElement = firstMessage();
    if (typeElement == null) {
      typeElement = firstEnum();
    }
    if (typeElement == null) {
      throw new IllegalArgumentException("Protobuf schema definition contains no type definitions");
    }
    String typeName = typeElement.getName();
    String packageName = schemaObj.getPackageName();
    return packageName != null && !packageName.isEmpty()
        ? packageName + '.' + typeName
        : typeName;
  }

  @Override
  public String canonicalString() {
    if (schemaObj == null) {
      return null;
    }
    if (canonicalString == null) {
      canonicalString = ProtobufSchemaUtils.toString(schemaObj);
    }
    return canonicalString;
  }

  @Override
  public String formattedString(String format) {
    if (SERIALIZED_FORMAT.equals(format)) {
      FileDescriptorProto file = toDynamicSchema().getFileDescriptorProto();
      return base64Encoder.encodeToString(file.toByteArray());
    }
    throw new IllegalArgumentException("Unsupported format " + format);
  }

  public Integer version() {
    return version;
  }

  @Override
  public List<SchemaReference> references() {
    return references;
  }

  public Map<String, String> resolvedReferences() {
    return dependencies.entrySet()
        .stream()
        .collect(Collectors.toMap(
                Map.Entry::getKey, e -> ProtobufSchemaUtils.toString(e.getValue())));
  }

  public Map<String, ProtoFileElement> dependencies() {
    return dependencies;
  }

  public Map<String, ProtoFileElement> dependenciesWithLogicalTypes() {
    Map<String, ProtoFileElement> deps = new HashMap<>(dependencies);
    for (Map.Entry<String, ProtoFileElement> entry : KNOWN_DEPENDENCIES.entrySet()) {
      if (!deps.containsKey(entry.getKey())) {
        deps.put(entry.getKey(), entry.getValue());
      }
    }
    return deps;
  }

  @Override
  public ProtobufSchema normalize() {
    String normalized = ProtobufSchemaUtils.toNormalizedString(this);
    return new ProtobufSchema(
        toProtoFile(normalized),
        this.version,
        this.name,
        this.references.stream().sorted().distinct().collect(Collectors.toList()),
        this.dependencies,
        normalized,
        null,
        null
    );
  }

  @Override
  public void validate() {
    toDynamicSchema();
  }

  @Override
  public List<String> isBackwardCompatible(ParsedSchema previousSchema) {
    if (!schemaType().equals(previousSchema.schemaType())) {
      return Collections.singletonList("Incompatible because of different schema type");
    }
    final List<Difference> differences = SchemaDiff.compare(
        (ProtobufSchema) previousSchema, this
    );
    final List<Difference> incompatibleDiffs = differences.stream()
        .filter(diff -> !SchemaDiff.COMPATIBLE_CHANGES.contains(diff.getType()))
        .collect(Collectors.toList());
    boolean isCompatible = incompatibleDiffs.isEmpty();
    if (!isCompatible) {
      boolean first = true;
      List<String> errorMessages = new ArrayList<>();
      for (Difference incompatibleDiff : incompatibleDiffs) {
        if (first) {
          // Log first incompatible change as warning
          log.warn("Found incompatible change: {}", incompatibleDiff);
          errorMessages.add(String.format("Found incompatible change: %s", incompatibleDiff));
          first = false;
        } else {
          log.debug("Found incompatible change: {}", incompatibleDiff);
          errorMessages.add(String.format("Found incompatible change: %s", incompatibleDiff));
        }
      }
      return errorMessages;
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProtobufSchema that = (ProtobufSchema) o;
    // Can't use schemaObj as locations may differ
    return Objects.equals(canonicalString(), that.canonicalString())
        && Objects.equals(references, that.references)
        && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    if (hashCode == NO_HASHCODE) {
      // Can't use schemaObj as locations may differ
      hashCode = Objects.hash(canonicalString(), references, version);
    }
    return hashCode;
  }

  @Override
  public String toString() {
    return canonicalString();
  }

  public String fullName() {
    Descriptor descriptor = toDescriptor();
    FileDescriptor fd = descriptor.getFile();
    DescriptorProtos.FileOptions o = fd.getOptions();
    String p = o.hasJavaPackage() ? o.getJavaPackage() : fd.getPackage();
    String outer = "";
    if (!o.getJavaMultipleFiles()) {
      if (o.hasJavaOuterClassname()) {
        outer = o.getJavaOuterClassname();
      } else {
        // Can't determine full name without either java_outer_classname or java_multiple_files
        return null;
      }
    }
    StringBuilder inner = new StringBuilder();
    while (descriptor != null) {
      if (inner.length() == 0) {
        inner.insert(0, descriptor.getName());
      } else {
        inner.insert(0, descriptor.getName() + "$");
      }
      descriptor = descriptor.getContainingType();
    }
    String d1 = (!outer.isEmpty() || inner.length() != 0 ? "." : "");
    String d2 = (!outer.isEmpty() && inner.length() != 0 ? "$" : "");
    return p + d1 + outer + d2 + inner;
  }

  public MessageIndexes toMessageIndexes(String name) {
    List<Integer> indexes = new ArrayList<>();
    String[] parts = name.split("\\.");
    List<TypeElement> types = schemaObj.getTypes();
    for (String part : parts) {
      int i = 0;
      for (TypeElement type : types) {
        if (type instanceof MessageElement) {
          if (type.getName().equals(part)) {
            indexes.add(i);
            types = type.getNestedTypes();
            break;
          }
          i++;
        }
      }
    }
    return new MessageIndexes(indexes);
  }

  public String toMessageName(MessageIndexes indexes) {
    StringBuilder sb = new StringBuilder();
    List<TypeElement> types = schemaObj.getTypes();
    boolean first = true;
    for (Integer index : indexes.indexes()) {
      if (!first) {
        sb.append(".");
      } else {
        first = false;
      }
      MessageElement message = getMessageAtIndex(types, index);
      if (message == null) {
        throw new IllegalArgumentException("Invalid message indexes: " + indexes);
      }
      sb.append(message.getName());
      types = message.getNestedTypes();
    }
    String messageName = sb.toString();
    String packageName = schemaObj.getPackageName();
    return packageName != null && !packageName.isEmpty()
        ? packageName + '.' + messageName
        : messageName;
  }

  private MessageElement getMessageAtIndex(List<TypeElement> types, int index) {
    int i = 0;
    for (TypeElement type : types) {
      if (type instanceof MessageElement) {
        if (index == i) {
          return (MessageElement) type;
        }
        i++;
      }
    }
    return null;
  }

  public static String toMapEntry(String s) {
    if (s.contains("_")) {
      s = LOWER_UNDERSCORE.to(UPPER_CAMEL, s);
    }
    return s + MAP_ENTRY_SUFFIX;
  }

  public static String toMapField(String s) {
    if (s.endsWith(MAP_ENTRY_SUFFIX)) {
      s = s.substring(0, s.length() - MAP_ENTRY_SUFFIX.length());
      s = UPPER_CAMEL.to(LOWER_UNDERSCORE, s);
    }
    return s;
  }
}
