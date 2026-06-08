package ru.mkb.msfo.application.intragroupops.upload;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class RowValidator {

  private static final String IFRS_CODE_PARTY_NOT_FOUND = "Код компании для МСФО '%s' не найден в справочнике составов группы и ставки налога";
  private static final String SHORT_NAME_PARTY_NOT_FOUND = "Сокращенное название компании '%s' не найден в справочнике составов группы и ставки налога";
  private static final String INN_PARTY_NOT_FOUND = "ИНН компании '%s' не найден в справочнике составов группы и ставки налога";
  private static final String REPORTING_SECTION_NOT_FOUND_MESSAGE = "Раздел отчетности '%s' не найден в справочнике разделов отчетности";
  private static final String FI_COMPONENT_NOT_FOUND = "Компонента ФИ '%s' не найден в справочнике cоставляющих финансовых инструментов";
  private static final String MSFO_ACC_NOT_FOUND = "Счет МСФО '%s' не найден в справочнике счетов МСФО";
  private static final String INVALID_REPORT_DATE = "Переданная дата, не соответствует дате текущего отчетного периода";
  private static final String IFRS_CODE_PARTY_NOT_MATCH_MESSAGE = "В загружаемом файле обнаружено несоответствие кодов компании для МСФО";
  private static final String VGO_FLAG_NOT_VALID_MESSAGE = "Для 'Признак для проводок ВГО' допустимы значения: %s";
  private static final String MSFO_CODE_PARTY_NOT_MATCH_MESSAGE_MKB_COMPANY = "Значение атрибута msfo_code_party1 или msfo_code_party2 не соответствует коду компании МКБ";
  private static final String LINKED_EXCLUDE_FROM_VGO_MESSAGE = "Сделки типа «Исключить из ВГО» не подлежат связыванию";

  private final LocalDate reportingDate;
  private final Set<String> groupCompositionClientCodes;
  private final Set<String> groupCompositionNames;
  private final Set<String> groupCompositionTaxIdNumbers;
  private final Set<String> sectionRefNumbers;
  private final Set<String> componentFiRefNames;
  private final Set<String> msfoAccountRefNumbers;
  private final Validator validator;
  private final String mkbCompanyCode;

  private String generalIfrsCodeParty1;
  private String generalIfrsCodeParty2;

  private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");

  RowValidator(ValidationScope validationScope) {
    this.reportingDate = validationScope.reportingDate();
    this.groupCompositionClientCodes = validationScope.groupCompositionsClientCodes();
    this.groupCompositionNames = validationScope.groupCompositionsNames();
    this.groupCompositionTaxIdNumbers = validationScope.groupCompositionsTaxIdNumbers();
    this.sectionRefNumbers = validationScope.sectionNames();
    this.componentFiRefNames = validationScope.componentFiNames();
    this.msfoAccountRefNumbers = validationScope.msfoAccountNumbers();
    this.mkbCompanyCode = validationScope.mkbCompanyCode();
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  List<ErrorWithRowNumbers> accumulateValidationErrorsAndReturn(IntraGroupOpRow intraGroupOpRow,
      int rowNumber) {
    List<ErrorWithRowNumbers> errors = new ArrayList<>();
    validateMsfoCodeParty(intraGroupOpRow.getIfrsCodeParty1(), rowNumber).ifPresent(errors::add);
    validateMsfoCodeParty(intraGroupOpRow.getIfrsCodeParty2(), rowNumber).ifPresent(errors::add);
    validateShortName(intraGroupOpRow.getShortNameParty1(), rowNumber).ifPresent(errors::add);
    validateShortName(intraGroupOpRow.getShortNameParty2(), rowNumber).ifPresent(errors::add);
    validateInnParty(intraGroupOpRow.getInnParty1(), rowNumber).ifPresent(errors::add);
    validateInnParty(intraGroupOpRow.getInnParty2(), rowNumber).ifPresent(errors::add);
    validateReportSection(intraGroupOpRow.getReportingSection(), rowNumber).ifPresent(errors::add);
    validateComponentFi(intraGroupOpRow.getFiComponent(), rowNumber).ifPresent(errors::add);
    validateMsfoAccount(intraGroupOpRow.getMsfoAccount(), rowNumber).ifPresent(errors::add);
    validateOnGeneralMsfoCodePartIdentity(intraGroupOpRow, rowNumber).ifPresent(errors::add);
    validateReportDate(intraGroupOpRow.getReportingDate(), rowNumber).ifPresent(errors::add);
    validateVgoFlagsValue(intraGroupOpRow.getVgoFlag(), rowNumber).ifPresent(errors::add);
    validateExcludeFromVgoLinkCode(intraGroupOpRow.getVgoFlag(), intraGroupOpRow.getLinkCode(),
        rowNumber).ifPresent(errors::add);
    validateCompanyCodesOnMkbExisting(intraGroupOpRow, rowNumber).ifPresent(errors::add);
    errors.addAll(validateOnConstraint(intraGroupOpRow, rowNumber));
    return errors;
  }

  private List<ErrorWithRowNumbers> validateOnConstraint(Object entry, int rowNumber) {
    return validator.validate(entry).stream()
        .map(it -> createError(it.getMessage(), rowNumber))
        .toList();
  }

  private Optional<ErrorWithRowNumbers> validateOnGeneralMsfoCodePartIdentity(
      IntraGroupOpRow intraGroupOpRow, int rowNumber) {
    String ifrsCodeParty1 = intraGroupOpRow.getIfrsCodeParty1();
    String ifrsCodeParty2 = intraGroupOpRow.getIfrsCodeParty2();

    if (ifrsCodeParty1 == null || ifrsCodeParty2 == null) {
      return Optional.empty();
    }
    initGeneralMsfoCodePartiesIfAbsent(ifrsCodeParty1, ifrsCodeParty2);
    return isIfrsCodePartiesEqual(ifrsCodeParty1, ifrsCodeParty2)
        ? Optional.empty()
        : Optional.of(createError(IFRS_CODE_PARTY_NOT_MATCH_MESSAGE, rowNumber));
  }

  private boolean isIfrsCodePartiesEqual(String ifrsCodeParty1, String ifrsCodeParty2) {
    return generalIfrsCodeParty1.equals(ifrsCodeParty1) && generalIfrsCodeParty2.equals(
        ifrsCodeParty2);
  }

  private void initGeneralMsfoCodePartiesIfAbsent(String msfoCodePart1, String msfoCodePart2) {
    if (generalIfrsCodeParty1 == null) {
      generalIfrsCodeParty1 = msfoCodePart1;
    }
    if (generalIfrsCodeParty2 == null) {
      generalIfrsCodeParty2 = msfoCodePart2;
    }
  }

  private Optional<ErrorWithRowNumbers> validateVgoFlagsValue(String vgoFlagValue, int rowNumber) {
    if (vgoFlagValue != null && !VgoFlag.isValidValue(vgoFlagValue)) {
      return Optional.of(
          createError(VGO_FLAG_NOT_VALID_MESSAGE.formatted(VgoFlag.getAllDisplayNames()),
              rowNumber));
    }
    return Optional.empty();
  }

  private Optional<ErrorWithRowNumbers> validateCompanyCodesOnMkbExisting(IntraGroupOpRow intraGroupOpRow, int rowNumber) {

    if (!mkbCompanyCode.equals(intraGroupOpRow.getIfrsCodeParty1()) && !mkbCompanyCode.equals(
        intraGroupOpRow.getIfrsCodeParty2())) {
      return Optional.of(
          createError(MSFO_CODE_PARTY_NOT_MATCH_MESSAGE_MKB_COMPANY, rowNumber)
      );
    }
    return Optional.empty();
  }

  private Optional<ErrorWithRowNumbers> validateExcludeFromVgoLinkCode(String vgoFlagValue,
      String linkCode, int rowNumber) {
    if (VgoFlag.EXCLUDE_FROM_VGO.equals(VgoFlag.fromString(vgoFlagValue)) && stringValueNotBlank(
        linkCode)) {
      return Optional.of(
          createError(LINKED_EXCLUDE_FROM_VGO_MESSAGE, rowNumber));
    }
    return Optional.empty();
  }

  private boolean stringValueNotBlank(String linkCode) {
    return linkCode != null && !linkCode.isBlank();
  }

  private Optional<ErrorWithRowNumbers> validateMsfoCodeParty(String value, int rowNumber) {
    return validateFieldByRefSet(value, groupCompositionClientCodes,
        IFRS_CODE_PARTY_NOT_FOUND.formatted(value), rowNumber);
  }

  private Optional<ErrorWithRowNumbers> validateShortName(String value, int rowNumber) {
    return validateFieldByRefSet(value, groupCompositionNames,
        SHORT_NAME_PARTY_NOT_FOUND.formatted(value), rowNumber);
  }

  private Optional<ErrorWithRowNumbers> validateInnParty(String value, int rowNumber) {
    return validateFieldByRefSet(value, groupCompositionTaxIdNumbers,
        INN_PARTY_NOT_FOUND.formatted(value), rowNumber);
  }

  private Optional<ErrorWithRowNumbers> validateReportSection(String value, int rowNumber) {
    return validateFieldByRefSet(value, sectionRefNumbers,
        REPORTING_SECTION_NOT_FOUND_MESSAGE.formatted(value), rowNumber);
  }

  private Optional<ErrorWithRowNumbers> validateComponentFi(String value, int rowNumber) {
    return validateFieldByRefSet(value, componentFiRefNames,
        FI_COMPONENT_NOT_FOUND.formatted(value), rowNumber);
  }

  private Optional<ErrorWithRowNumbers> validateMsfoAccount(String value, int rowNumber) {
    return validateFieldByRefSet(value, msfoAccountRefNumbers,
        MSFO_ACC_NOT_FOUND.formatted(value), rowNumber);
  }

  private Optional<ErrorWithRowNumbers> validateFieldByRefSet(String value,
      Set<String> refSet, String errorMsg, int rowNumber) {
    if (value == null) {
      return Optional.empty();
    }
    return isAbsentIntoRef(refSet, value)
        ? Optional.of(createError(errorMsg.formatted(value), rowNumber))
        : Optional.empty();
  }

  private boolean isAbsentIntoRef(Set<String> ref, String value) {
    return ref == null || !ref.contains(value);
  }

  private Optional<ErrorWithRowNumbers> validateReportDate(String reportingDate, int rowNumber) {
    try {
      if (reportingDate != null && !isReportDateValid(reportingDate)) {
        return Optional.of(createError(INVALID_REPORT_DATE, rowNumber));
      }
      return Optional.empty();
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  private boolean isReportDateValid(String reportingDate) {
    return this.reportingDate.equals(LocalDate.parse(reportingDate, formatter));
  }

  private ErrorWithRowNumbers createError(String message, int rowNumber) {
    return new ErrorWithRowNumbers(message, Collections.singletonList(rowNumber));
  }

  record ErrorWithRowNumbers(String message, List<Integer> rowNumbers) {
  }
}
