package com.axelor.apps.base.exceptions;

public final class AdminExceptionMessage {

  private AdminExceptionMessage() {}

  public static final String NO_CONFIG_REQUIRED = /*$$(*/ "No configuration required" /*)*/;

  public static final String APP_IN_USE = /*$$(*/
      "This app is used by %s. Please deactivate them before continue." /*)*/;

  public static final String BULK_INSTALL_SUCCESS = /*$$(*/ "Apps installed successfully" /*)*/;

  public static final String REFRESH_APP_SUCCESS = /*$$(*/ "Apps refreshed successfully" /*)*/;

  public static final String REFRESH_APP_ERROR = /*$$(*/ "Error in refreshing app" /*)*/;

  public static final String NO_LANGUAGE_SELECTED = /*$$(*/
      "No application language set. Please set 'application.locale' property." /*)*/;

  public static final String DEMO_DATA_SUCCESS = /*$$(*/ "Demo data loaded successfully" /*)*/;

  public static final String ACCESS_CONFIG_IMPORTED = /*$$(*/
      "Access config imported successfully" /*)*/;

  public static final String OBJECT_DATA_REPLACE_MISSING = /*$$(*/ "No record found for: %s" /*)*/;

  public static final String ROLE_IMPORT_SUCCESS = /*$$(*/ "Roles imported successfully" /*)*/;

  public static final String DATA_EXPORT_DIR_ERROR = /*$$(*/ "Export path not configured" /*)*/;

  public static final String FILE_UPLOAD_DIR_ERROR = /*$$(*/
      "File upload path not configured" /*)*/;

  public static final String EMPTY_RELATIONAL_FIELD_IN_DATA_CONFIG_LINE = /*$$(*/
      "The 'Relational field' of the line '%s' cannot be empty." /*)*/;
  public static final String EMPTY_QUERY_IN_DATA_CONFIG_LINE = /*$$(*/
      "The 'Query' of the line '%s' cannot be empty." /*)*/;

  public static final String FAKER_METHOD_DOES_NOT_EXIST = /*$$(*/
      "The method '%s' doesn't exist in the Faker API." /*)*/;

  public static final String FAKER_CLASS_DOES_NOT_EXIST = /*$$(*/
      "The class '%s' doesn't exist in the Faker API." /*)*/;

  public static final String FAKER_METHOD_ERROR = /*$$(*/
      "An error occured while executing '%s'." /*)*/;

  public static final String UNIT_TEST_IMPORT_SUCCESS = /*$$(*/ "Import Successfull" /*)*/;

  public static final String UNIT_TEST_EXPORT_RECORD_NOT_SPECIFIED = /*$$(*/
      "Please select atleast one record to export" /*)*/;

  public static final String UNIT_TEST_IMPORT_NAME_NOT_SPECIFIED = /*$$(*/
      "Please specify unit test name" /*)*/;

  public static final String UNIT_TEST_TARGET_VAR_NOT_SPECIFIED = /*$$(*/
      "Target variable is undefined" /*)*/;

  public static final String UNIT_TEST_INVALID_ACTION_NAME = /*$$(*/
      "Action name is invalid no such action found" /*)*/;

  public static final String UNIT_TEST_INVALID_TARGET_FOR_ADD = /*$$(*/
      "Target must be reference field" /*)*/;

  public static final String UNIT_TEST_INVALID_MODEL_NAME = /*$$(*/
      "%s is not valid model name" /*)*/;

  public static final String UNIT_TEST_INVALID_SELECT_FORMAT = /*$$(*/
      "Format should be ModelName : jpql-filter with valid model name" /*)*/;

  public static final String UNIT_TEST_ACTION_SOMETHING_WRONG = /*$$(*/
      "Something went wrong in action %s" /*)*/;

  public static final String UNIT_TEST_ERROR_FROM_ACTION = /*$$(*/
      "Error in action <b>%s</b><br/><li>%s</li>" /*)*/;

  public static final String UNIT_TEST_ALERT_FROM_ACTION = /*$$(*/
      "Alert from action <b>%s</b><br/><li>%s</li>" /*)*/;

  public static final String UNIT_TEST_FLASH_FROM_ACTION = /*$$(*/
      "Message from action <b>%s</b><br/><li>%s</li>" /*)*/;
}
