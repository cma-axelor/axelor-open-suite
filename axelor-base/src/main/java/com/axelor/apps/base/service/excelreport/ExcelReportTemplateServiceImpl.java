/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.base.service.excelreport;

import com.axelor.apps.base.db.Print;
import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.db.ReportQueryBuilder;
import com.axelor.apps.base.db.ReportQueryBuilderParams;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.PrintTemplateService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.excelreport.HtmlToExcel.RichTextDetails;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.QueryBinder;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.MetaStore;
import com.axelor.rpc.Context;
import com.axelor.script.GroovyScriptHelper;
import com.axelor.script.ScriptHelper;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.itextpdf.awt.geom.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.persistence.Query;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.transform.BasicTransformerAdapter;

public class ExcelReportTemplateServiceImpl implements ExcelReportTemplateService {

  @Inject protected CellMergingService cellMergingService;

  // final constants
  private static final String KEY_ROW = "Row";
  private static final String KEY_COLUMN = "Column";
  private static final String KEY_VALUE = "Value";
  private static final String KEY_CELL_STYLE = "CellStyle";
  private static final String HEADER_SHEET_TITLE = "Header";
  private static final String TEMPLATE_SHEET_TITLE = "Template";
  private static final String FOOTER_SHEET_TITLE = "Footer";

  // variable parameters
  private int maxRows = 100;
  private int maxColumns = 100;
  private int rowNumber = 0;
  private String modelName;
  private int mergeOffset = 0;
  private List<CellRangeAddress> mergedCellsRangeAddressList;
  private Set<CellRangeAddress> mergedCellsRangeAddressSetPerSheet;
  private int BIGDECIMAL_SCALE = 2;
  private Map<Integer, Map<String, Object>> headerInputMap;
  private Map<Integer, Map<String, Object>> footerInputMap;
  private Map<String, Set<CellRangeAddress>> headerFooterMergedCellsMap = new HashMap<>();
  private Set<CellRangeAddress> blankMergedCellsRangeAddressSet = new HashSet<>();
  private int collectionEntryRow = -1;
  private int record;
  private boolean nextRowCheckActive = false;
  private Map<
          String, List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>>
      pictureInputMap = new HashMap<>();
  private Map<String, Map<String, List<ImmutablePair<Integer, Integer>>>> pictureRowShiftMap =
      new HashMap<>();
  private XSSFSheet originSheet;
  private Map<String, ImmutablePair<XSSFSheet, XSSFSheet>> headerFooterSheetMap = new HashMap<>();
  private Print print = null;
  private List<Integer> removeCellKeyList = new ArrayList<>();
  private ResourceBundle resourceBundle;
  private List<ReportQueryBuilder> reportQueryBuilderList;

  @Override
  public File createReport(List<Long> objectIds, PrintTemplate printTemplate) throws Exception {

    // Fetch all parameters from print template
    File file = MetaFiles.getPath(printTemplate.getExcelTemplate()).toFile();
    String modelFullName = printTemplate.getMetaModel().getFullName();
    modelName = printTemplate.getMetaModel().getName();
    String formatType = printTemplate.getFormatSelect();
    boolean isLandscape = printTemplate.getDisplayTypeSelect() == 2;
    BigDecimal dataSizeReduction = printTemplate.getDataSizeReduction();
    maxRows = Beans.get(AppBaseService.class).getAppBase().getMaxRows();
    maxColumns = Beans.get(AppBaseService.class).getAppBase().getMaxColumns();
    print = Beans.get(PrintTemplateService.class).generatePrint(objectIds.get(0), printTemplate);
    resourceBundle =
        ObjectUtils.notEmpty(printTemplate.getLanguage())
            ? getResourceBundle(printTemplate.getLanguage().getCode())
            : getResourceBundle(null);
    if (ObjectUtils.notEmpty(printTemplate.getReportQueryBuilderList())) {
      reportQueryBuilderList = new ArrayList<>(printTemplate.getReportQueryBuilderList());
    }

    int scale = Beans.get(AppBaseService.class).getAppBase().getBigdecimalScale();
    if (scale != 0) BIGDECIMAL_SCALE = scale;

    List<Model> result = this.getModelData(modelFullName, objectIds);

    XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(file));
    Map<Integer, Map<String, Object>> inputMap = this.getInputMap(wb, TEMPLATE_SHEET_TITLE);
    this.getHeadersAndFooters(wb);

    XSSFWorkbook newWb =
        this.createXSSFWorkbook(inputMap, result, this.getMapper(modelFullName), formatType, wb);
    wb.close();
    File outputFile = MetaFiles.createTempFile(I18n.get(modelName), ".xlsx").toFile();
    FileOutputStream outputStream = new FileOutputStream(outputFile.getAbsolutePath());
    newWb.write(outputStream);
    newWb.close();

    if (formatType.equals("XLSX")) {
      return outputFile;
    } else if (formatType.equals("PDF")) {
      ZipSecureFile.setMinInflateRatio(0);
      return Beans.get(ExcelToPdf.class)
          .createPdfFromExcel(
              outputFile,
              headerFooterSheetMap,
              pictureInputMap,
              pictureRowShiftMap,
              !isLandscape,
              dataSizeReduction,
              print);
    } else {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  protected <T extends Model> List<T> getModelData(String modelFullName, List<Long> ids)
      throws ClassNotFoundException {
    Class<T> modelClass = (Class<T>) Class.forName(modelFullName);
    return JpaRepository.of(modelClass).all().filter("id in :ids").bind("ids", ids).fetch();
  }

  protected Mapper getMapper(String modelFullName) throws ClassNotFoundException {
    Class<?> klass = Class.forName(modelFullName);
    return Mapper.of(klass);
  }

  @SuppressWarnings("resource")
  protected Map<Integer, Map<String, Object>> getInputMap(XSSFWorkbook wb, String sheetName)
      throws IOException, AxelorException {
    Map<Integer, Map<String, Object>> map = new HashMap<>();

    int lastColumn = 0;

    XSSFSheet sheet;

    if (sheetName.equalsIgnoreCase(HEADER_SHEET_TITLE)
        && ObjectUtils.notEmpty(print.getPrintPdfHeader())) {
      Map<String, Object> dataMap = new HashMap<>();
      dataMap.put(KEY_ROW, 1);
      dataMap.put(KEY_COLUMN, 1);
      dataMap.put(KEY_VALUE, "");
      dataMap.put(KEY_CELL_STYLE, wb.createCellStyle());
      map.put(0, dataMap);
    }

    if (sheetName.equalsIgnoreCase(FOOTER_SHEET_TITLE)
        && ObjectUtils.notEmpty(print.getPrintPdfFooter())) {

      Font font = wb.createFont();
      font.setFontName(
          print.getFooterFontType() != null ? print.getFooterFontType() : "Times Roman");
      font.setFontHeightInPoints(
          print.getFooterFontSize().equals(BigDecimal.ZERO)
              ? (short) 10
              : print.getFooterFontSize().shortValue());
      font.setColor(getCellFooterFontColor(print.getFooterFontColor()));
      if (print.getIsFooterUnderLine()) {
        font.setUnderline(Font.U_SINGLE);
      }
      XSSFCellStyle cellStyle = wb.createCellStyle();
      cellStyle.setFont(font);

      Map<String, Object> dataMap = new HashMap<>();
      dataMap.put(KEY_ROW, 1);
      dataMap.put(KEY_COLUMN, 1);
      dataMap.put(KEY_VALUE, print.getPrintPdfFooter());
      dataMap.put(KEY_CELL_STYLE, cellStyle);
      map.put(0, dataMap);
    }

    if (wb.getSheet(sheetName) == null) return map;

    sheet = wb.getSheet(sheetName);

    if (sheetName.equalsIgnoreCase(TEMPLATE_SHEET_TITLE)) {
      mergedCellsRangeAddressList = sheet.getMergedRegions();
      originSheet = sheet;
    }

    if (sheetName.equalsIgnoreCase(HEADER_SHEET_TITLE)) {
      headerFooterMergedCellsMap.put(HEADER_SHEET_TITLE, new HashSet<>(sheet.getMergedRegions()));
    }

    if (sheetName.equalsIgnoreCase(FOOTER_SHEET_TITLE)) {
      headerFooterMergedCellsMap.put(FOOTER_SHEET_TITLE, new HashSet<>(sheet.getMergedRegions()));
    }

    this.getPictures(sheet, sheetName);

    int n = 0;

    for (int i = 0; i < maxRows; i++) {
      XSSFRow row = sheet.getRow(i);
      if (ObjectUtils.notEmpty(row)) {
        for (int j = 0; j < maxColumns; j++) {
          XSSFCell cell = row.getCell(j);
          if (ObjectUtils.isEmpty(cell) || isCellEmpty(cell)) {
            continue;
          }

          map.put(n, this.getDataMap(cell));
          if (lastColumn < cell.getColumnIndex()) lastColumn = cell.getColumnIndex();
          n++;
        }
      }
    }

    return map;
  }

  protected void getPictures(XSSFSheet sheet, String sheetName) {
    ImmutablePair<Integer, Integer> pair;
    ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>> triple;
    List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>> tripleList =
        new ArrayList<>();

    XSSFDrawing drawing = sheet.getDrawingPatriarch();
    if (ObjectUtils.notEmpty(drawing)) {
      for (XSSFShape shape : drawing.getShapes()) {
        if (shape instanceof Picture) {
          XSSFPicture picture = (XSSFPicture) shape;
          pair =
              new ImmutablePair<>(
                  picture.getClientAnchor().getRow1(), picture.getClientAnchor().getRow2());
          triple =
              new ImmutableTriple<>(picture, this.getDimensions(sheet, picture, sheetName), pair);

          tripleList.add(triple);
        }
      }
      pictureInputMap.put(sheetName, tripleList);
    }
  }

  protected Dimension getDimensions(XSSFSheet sheet, XSSFPicture picture, String sheetName) {
    int width = 0;
    int height = 0;
    Set<CellRangeAddress> mergedCellsList;
    if (sheetName.equals(HEADER_SHEET_TITLE) || sheetName.equals(FOOTER_SHEET_TITLE)) {
      mergedCellsList = headerFooterMergedCellsMap.get(sheetName);
    } else {
      mergedCellsList = new HashSet<>(mergedCellsRangeAddressList);
    }

    int firstRow;
    int lastRow;
    int firstColumn;
    int lastColumn;
    firstRow = picture.getClientAnchor().getRow1();
    firstColumn = picture.getClientAnchor().getCol1();

    CellRangeAddress cellR = null;

    for (CellRangeAddress cellRange : mergedCellsList) {

      if (cellRange.isInRange(firstRow, firstColumn)) {
        cellR = cellRange;
        break;
      }
    }

    if (ObjectUtils.notEmpty(cellR)) {

      lastRow = cellR.getLastRow();
      lastColumn = cellR.getLastColumn();
      for (int i = firstRow; i <= lastRow; i++) {
        if (ObjectUtils.notEmpty(sheet.getRow(i))) height += sheet.getRow(i).getHeight() / 20f;
      }
      for (int i = firstColumn; i <= lastColumn; i++) {

        width += sheet.getColumnWidthInPixels(i);
      }
    }

    return new Dimension(width / 2f, height);
  }

  protected Map<String, Object> getDataMap(XSSFCell cell) throws AxelorException {
    Map<String, Object> map = new HashMap<>();
    Object cellValue = getCellValue(cell);
    map.put(KEY_ROW, cell.getRowIndex());
    map.put(KEY_COLUMN, cell.getColumnIndex());
    map.put(KEY_VALUE, cellValue);
    map.put(KEY_CELL_STYLE, cell.getCellStyle());

    return map;
  }

  protected XSSFWorkbook createXSSFWorkbook(
      Map<Integer, Map<String, Object>> inputMap,
      List<Model> data,
      Mapper mapper,
      String formatType,
      XSSFWorkbook wb)
      throws AxelorException, ScriptException, IOException {
    XSSFWorkbook newWb = new XSSFWorkbook();

    Map<Integer, Map<String, Object>> headerOutputMap = new HashMap<>();
    Map<Integer, Map<String, Object>> footerOutputMap = new HashMap<>();

    Map<Integer, Map<String, Object>> outputMap = new HashMap<>();

    mergeOffset = cellMergingService.setMergeOffset(inputMap, mapper, mergedCellsRangeAddressList);

    int i = 1;
    XSSFSheet headerSheet;
    XSSFSheet footerSheet;

    for (Model dataItem : data) {
      String sheetName = String.format("%s %s", modelName, i++);
      headerOutputMap =
          this.getOutputMap(headerInputMap, mapper, dataItem, HEADER_SHEET_TITLE, sheetName, wb);
      footerOutputMap =
          this.getOutputMap(footerInputMap, mapper, dataItem, FOOTER_SHEET_TITLE, sheetName, wb);
      outputMap =
          this.getOutputMap(inputMap, mapper, dataItem, TEMPLATE_SHEET_TITLE, sheetName, wb);

      // hide collections if any and recalculate
      if (ObjectUtils.notEmpty(removeCellKeyList)) {
        outputMap =
            this.getOutputMap(
                getHideCollectionInputMap(inputMap),
                mapper,
                dataItem,
                TEMPLATE_SHEET_TITLE,
                sheetName,
                wb);
      }

      XSSFSheet newSheet = newWb.createSheet(sheetName);

      if (formatType.equals("XLSX")) {
        newSheet = this.setHeader(newSheet, outputMap, headerOutputMap);
        newSheet = this.writeTemplateSheet(outputMap, newSheet, 0);
        newSheet = this.setFooter(newSheet, footerOutputMap);
      } else if (formatType.equals("PDF")) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        headerSheet = null;
        footerSheet = null;
        if (ObjectUtils.notEmpty(headerOutputMap))
          headerSheet =
              this.createSheet(
                  workbook, headerOutputMap, HEADER_SHEET_TITLE, HEADER_SHEET_TITLE + i);

        if (ObjectUtils.notEmpty(footerOutputMap))
          footerSheet =
              this.createSheet(
                  workbook, footerOutputMap, FOOTER_SHEET_TITLE, FOOTER_SHEET_TITLE + i);

        headerFooterSheetMap.put(sheetName, new ImmutablePair<>(headerSheet, footerSheet));
        newSheet = this.writeTemplateSheet(outputMap, newSheet, 0);
      }

      this.resetPictureMap();
    }

    return newWb;
  }

  private Map<Integer, Map<String, Object>> getHideCollectionInputMap(
      Map<Integer, Map<String, Object>> inputMap) {
    List<ImmutablePair<Integer, Integer>> removeCellRowColumnPair = new ArrayList<>();
    List<Integer> finalRemoveKeyPairList = new ArrayList<>();
    // new input map
    Map<Integer, Map<String, Object>> newInputMap = new HashMap<>(inputMap);

    // get location of all cells to hide
    for (Integer key : removeCellKeyList) {
      Integer row = (Integer) inputMap.get(key).get(KEY_ROW);
      Integer column = (Integer) inputMap.get(key).get(KEY_COLUMN);
      removeCellRowColumnPair.add(new ImmutablePair<>(row, column));
      removeCellRowColumnPair.add(new ImmutablePair<>(row + 1, column));
      removeCellRowColumnPair.add(new ImmutablePair<>(row - 1, column));
    }

    // get all hiding cell keys
    for (Map.Entry<Integer, Map<String, Object>> entry : inputMap.entrySet()) {
      for (ImmutablePair<Integer, Integer> pair : removeCellRowColumnPair) {
        if (entry.getValue().get(KEY_ROW).equals(pair.getLeft())
            && entry.getValue().get(KEY_COLUMN).equals(pair.getRight())) {
          finalRemoveKeyPairList.add(entry.getKey());
        }
      }
    }

    // shift cells to left which occur after the cells to remove
    for (Map.Entry<Integer, Map<String, Object>> entry : inputMap.entrySet()) {
      for (ImmutablePair<Integer, Integer> pair : removeCellRowColumnPair) {
        if (entry.getValue().get(KEY_ROW).equals(pair.getLeft())
            && (Integer) entry.getValue().get(KEY_COLUMN) > (pair.getRight())) {
          newInputMap
              .get(entry.getKey())
              .replace(KEY_COLUMN, (Integer) entry.getValue().get(KEY_COLUMN) - 1);
        }
      }
    }

    // remove cells to hide
    for (Integer key : finalRemoveKeyPairList) {
      newInputMap.remove(key);
    }

    return newInputMap;
  }

  private void resetPictureMap() {
    List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>> tripleList =
        pictureInputMap.get(TEMPLATE_SHEET_TITLE);

    if (ObjectUtils.isEmpty(tripleList)) return;

    for (ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>> triple :
        tripleList) {
      triple.getLeft().getClientAnchor().setRow1(triple.getRight().getLeft());
      triple.getLeft().getClientAnchor().setRow2(triple.getRight().getRight());
    }

    tripleList = pictureInputMap.get(FOOTER_SHEET_TITLE);

    if (ObjectUtils.isEmpty(tripleList)) return;

    for (ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>> triple :
        tripleList) {
      triple.getLeft().getClientAnchor().setRow1(triple.getRight().getLeft());
      triple.getLeft().getClientAnchor().setRow2(triple.getRight().getRight());
    }
  }

  protected XSSFSheet createSheet(
      XSSFWorkbook workbook, Map<Integer, Map<String, Object>> map, String name, String sheetName) {

    XSSFSheet sheet = workbook.createSheet(sheetName);
    sheet = this.write(map, sheet, 0, false);
    this.fillMergedRegionCells(sheet);

    this.setMergedRegionsInSheet(sheet, headerFooterMergedCellsMap.get(name));
    return sheet;
  }

  protected void writePictures(
      XSSFSheet sheet,
      List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>
          pictureTripleList,
      String sheetType) {
    XSSFWorkbook workbook = sheet.getWorkbook();
    XSSFPicture picture;
    for (ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>> triple :
        pictureTripleList) {
      picture = triple.getLeft();
      int pictureIndex =
          workbook.addPicture(picture.getPictureData().getData(), Workbook.PICTURE_TYPE_PNG);
      CreationHelper helper = workbook.getCreationHelper();
      Drawing<?> drawing = sheet.createDrawingPatriarch();
      ClientAnchor anchor = helper.createClientAnchor();
      anchor.setCol1(picture.getClientAnchor().getCol1());
      anchor.setCol2(picture.getClientAnchor().getCol2());

      int offset = 0;
      if (sheetType.equalsIgnoreCase(TEMPLATE_SHEET_TITLE)) {
        int firstRow = picture.getClientAnchor().getRow1();
        Optional<ImmutablePair<Integer, Integer>> optionalPair =
            pictureRowShiftMap.get(sheet.getSheetName()).get(TEMPLATE_SHEET_TITLE).stream()
                .filter(p -> p.getLeft() == firstRow)
                .findFirst();
        if (optionalPair.isPresent()) offset = optionalPair.get().getRight();
      }

      anchor.setRow1(picture.getClientAnchor().getRow1() + offset);
      anchor.setRow2(picture.getClientAnchor().getRow2() + offset);
      drawing.createPicture(anchor, pictureIndex);
    }
  }

  protected Triple<String, String, String> getOperatingTriple(String formula) {

    Triple<String, String, String> triple = null;

    Pattern p = Pattern.compile("\\((.*?)\\)");
    Matcher m = p.matcher(formula);
    String expr = null;
    if (m.find()) {
      expr = formula.substring(m.start() + 1, m.end());
    }
    String type = "SUM";
    if (expr.toLowerCase().startsWith("sum")) {
      type = "SUM";
    }
    m = p.matcher(expr);
    String params[] = null;
    if (m.find()) {
      String paramString = expr.substring(m.start() + 1, m.end() - 1);
      params = paramString.split("\\s*,\\s*");
    }

    if (params.length > 2) {
      String condition = params[1];
      for (int i = 2; i < params.length; i++)
        condition = String.format("%s AND %s", condition, params[i]);
      triple = Triple.of(type, params[0], condition);
    } else if (params.length == 2) {
      triple = Triple.of(type, params[0], params[1]);
    } else {
      triple = Triple.of(type, params[0], null);
    }

    return triple;
  }

  @SuppressWarnings("unchecked")
  protected Map<Integer, Map<String, Object>> getOutputMap(
      Map<Integer, Map<String, Object>> inputMap,
      Mapper mapper,
      Object object,
      String sheetType,
      String sheetName,
      XSSFWorkbook wb)
      throws AxelorException, IOException, ScriptException {

    mergedCellsRangeAddressSetPerSheet = new HashSet<>();
    collectionEntryRow = -1;
    record = 0;
    Map<Integer, Map<String, Object>> outputMap = new HashMap<>(inputMap);
    Object mainObject = object;
    Property property = null;
    int index = inputMap.size();

    int totalRecord = 0;

    long recordId = (long) mapper.getProperty("id").get(object);
    if (sheetType.equalsIgnoreCase(TEMPLATE_SHEET_TITLE)) {
      blankMergedCellsRangeAddressSet =
          cellMergingService.getBlankMergedCells(
              originSheet, mergedCellsRangeAddressList, sheetType);
      mergedCellsRangeAddressSetPerSheet.addAll(blankMergedCellsRangeAddressSet);
    }

    for (Map.Entry<Integer, Map<String, Object>> entry : inputMap.entrySet()) {
      Map<String, Object> m = new HashMap<>(entry.getValue());
      boolean hide = false; // groovy condition boolean
      boolean translate = false; // language translation boolean
      String operationString = null;

      if (nextRowCheckActive) {
        if ((int) m.get(KEY_ROW) > collectionEntryRow) {
          this.setPictureRowShiftMap(sheetName, sheetType, collectionEntryRow);
          totalRecord = totalRecord + record;
          record = 0;
        }
        nextRowCheckActive = false;
      }

      Object cellValue = m.get(KEY_VALUE);
      String value = cellValue == null ? null : cellValue.toString();

      // check for translation function
      if (value.trim().startsWith("_t(value:")
          && (value.trim().contains("hide") || value.trim().contains("show"))) {
        translate = true;
        value =
            org.apache.commons.lang3.StringUtils.replaceOnce(
                value.trim().replace("_t(value:", "").trim(), ")", "");

      } else if (value.trim().startsWith("_t(value:")) {
        translate = true;
        value = org.apache.commons.lang3.StringUtils.chop(value.trim().replace("_t(value:", ""));
      } else if (value.trim().startsWith("_t('") || value.trim().startsWith("_t(‘")) {
        translate = true;
        value =
            value
                .trim()
                .replace("_t('", "")
                .replace("_t(‘", "")
                .replace("')", "")
                .replace("’)", "");
      }

      outputMap.put(entry.getKey(), m);
      if (StringUtils.notBlank(value)) {
        if (value.contains("$")) {

          String propertyName = value;

          if (!value.contains("$formula")) {

            // Check for groovy conditional text
            if (propertyName.contains(":")
                && (propertyName.contains("hide") || propertyName.contains("show"))) {
              hide = getConditionResult(propertyName, object);
              propertyName = propertyName.substring(0, propertyName.indexOf(":")).trim();
            } else if (propertyName.startsWith("if") && propertyName.contains("->")) {
              ImmutablePair<String, String> valueOperationPair =
                  getIfConditionResult(propertyName, object);

              propertyName = valueOperationPair.getLeft();
              operationString = valueOperationPair.getRight();
            } else if (propertyName.contains(":") && propertyName.startsWith("$eval:")) {
              m.replace(
                  KEY_VALUE,
                  validateCondition(propertyName.substring(propertyName.indexOf(":") + 1), object));
              continue;
            }

            if (propertyName.contains("_t(value:")) {
              translate = true;
              propertyName =
                  org.apache.commons.lang3.StringUtils.chop(
                      propertyName.trim().replace("_t(value:", ""));
            }

            propertyName = propertyName.substring(1);
            property = this.getProperty(mapper, propertyName);

            if (ObjectUtils.isEmpty(property)) {
              if (!propertyName.contains(".") || ObjectUtils.isEmpty(reportQueryBuilderList)) {
                XSSFCellStyle newCellStyle = wb.createCellStyle();
                newCellStyle.setFont(((XSSFCellStyle) m.get(KEY_CELL_STYLE)).getFont());
                m.replace(KEY_VALUE, "");
                m.replace(KEY_CELL_STYLE, newCellStyle);
                continue;
              }
              Map<String, Map<String, Object>> reportQuery =
                  getReportQueryBuilderQuery(propertyName, object);

              if (ObjectUtils.notEmpty(reportQuery)) {
                String queryString = reportQuery.keySet().stream().findFirst().get();
                Map<String, Object> context = reportQuery.get(queryString);
                Query query = JPA.em().createQuery(queryString);
                query
                    .unwrap(org.hibernate.query.Query.class)
                    .setResultTransformer(new DataSetTransformer());
                QueryBinder.of(query).bind(context);
                List<Object> collection = query.getResultList();
                String key = propertyName.substring(propertyName.indexOf(".") + 1);

                // throw error if no such field found in report query
                if (ObjectUtils.isEmpty(collection)
                    || ((ObjectUtils.notEmpty(collection)
                        && !((LinkedHashMap<String, String>) collection.get(0))
                            .containsKey(key)))) {
                  throw new AxelorException(
                      TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                      "No records found for : " + propertyName);
                }

                Map<String, Object> entryValueMap = new HashMap<>(entry.getValue());

                if (collectionEntryRow != (int) entryValueMap.get(KEY_ROW)) {
                  collectionEntryRow = (int) entryValueMap.get(KEY_ROW);
                }

                rowNumber = (Integer) entryValueMap.get(KEY_ROW);

                ImmutablePair<Integer, Map<Integer, Map<String, Object>>> collectionEntryPair;
                this.setMergedCellsRangeAddressSetPerSheet(entryValueMap, collection, totalRecord);

                collectionEntryPair =
                    this.getReportQueryBuilderCollectionEntry(
                        outputMap,
                        entryValueMap,
                        collection,
                        entry,
                        key,
                        index,
                        totalRecord,
                        hide,
                        operationString,
                        translate);
                outputMap = collectionEntryPair.getRight();
                totalRecord = collectionEntryPair.getLeft();

                continue;
              } else {
                throw new AxelorException(
                    TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                    IExceptionMessage.NO_SUCH_FIELD + propertyName);
              }
            }

            if (!property.isCollection()) {

              if (hide) {
                m.replace(KEY_VALUE, "");
                continue;
              }

              m =
                  this.getNonCollectionEntry(
                      m,
                      mapper,
                      property,
                      mainObject,
                      propertyName,
                      totalRecord,
                      operationString,
                      translate);

            } else {

              Map<String, Object> entryValueMap = new HashMap<>(entry.getValue());
              if (!propertyName.contains(".")) {
                m.replace(KEY_VALUE, property.getTitle());
                this.shiftRows(m, false, totalRecord);
                continue;
              }

              if (collectionEntryRow != (int) entryValueMap.get(KEY_ROW)) {
                collectionEntryRow = (int) entryValueMap.get(KEY_ROW);
              }

              rowNumber = (Integer) entryValueMap.get(KEY_ROW);

              Collection<Object> collection = (Collection<Object>) property.get(object);
              ImmutablePair<Integer, Map<Integer, Map<String, Object>>> collectionEntryPair;
              this.setMergedCellsRangeAddressSetPerSheet(entryValueMap, collection, totalRecord);

              collectionEntryPair =
                  this.getCollectionEntry(
                      outputMap,
                      entryValueMap,
                      collection,
                      entry,
                      property,
                      propertyName,
                      index,
                      totalRecord,
                      hide,
                      operationString,
                      translate);
              outputMap = collectionEntryPair.getRight();
              totalRecord = collectionEntryPair.getLeft();
            }
          } else {
            if (propertyName.contains(":")) {
              hide = getConditionResult(value, object);
              value = value.substring(0, value.indexOf(":")).trim();
            }
            outputMap.put(
                entry.getKey(),
                this.getFormulaResultMap(entry, value, totalRecord, recordId, hide));
          }
          object = mainObject;
        } else {

          value = getLabel(value, object, translate);

          m.replace(KEY_VALUE, value);
          this.shiftRows(m, false, totalRecord);
        }
      } else {

        this.shiftRows(m, true, totalRecord);
      }
    }

    return outputMap;
  }

  @SuppressWarnings("serial")
  private static final class DataSetTransformer extends BasicTransformerAdapter {

    @Override
    public Object transformTuple(Object[] tuple, String[] aliases) {
      Map<String, Object> result = new LinkedHashMap<>(tuple.length);
      for (int i = 0; i < tuple.length; ++i) {
        String alias = aliases[i];
        if (alias != null) {
          result.put(alias, tuple[i]);
        }
      }
      return result;
    }
  }

  private Map<String, Map<String, Object>> getReportQueryBuilderQuery(
      String propertyName, Object bean) {
    String queryString = null;
    Map<String, Map<String, Object>> query = new HashMap<>();
    for (ReportQueryBuilder rqb : reportQueryBuilderList) {
      if (rqb.getVar().equals(propertyName.substring(0, propertyName.indexOf(".")))) {
        queryString = rqb.getQueryText();
        Map<String, Object> context = new HashMap<>();
        if (ObjectUtils.notEmpty(rqb.getReportQueryBuilderParamsList())) {

          for (ReportQueryBuilderParams params : rqb.getReportQueryBuilderParamsList()) {
            String expression = params.getValue();
            Object value = null;
            if (expression.trim().startsWith("eval:")) {
              value = this.validateCondition(expression, bean).toString();
            } else {
              value = expression;
            }
            context.put(params.getName(), value);
          }
        }
        query.put(queryString, context);
        break;
      }
    }
    return query;
  }

  private String getLabel(String value, Object bean, boolean translate)
      throws IOException, AxelorException {
    if (value.contains(" : ") && (value.contains("hide") || value.contains("show"))) {
      if (getConditionResult(value, bean)) {
        value = "";
      } else {
        value = value.substring(0, value.lastIndexOf(" : ")).trim();

        if (isTranslationFunction(value)) {

          value = getTranslatedValue(value).toString();
        }
      }
    } else if (value.startsWith("if") && value.contains("->")) { // if else condition
      value = getIfConditionResult(value, bean).getLeft();
      if (isTranslationFunction(value)) {
        value = getTranslatedValue(value).toString();
      }
    }
    if (translate) {
      value = resourceBundle.getString(value);
    }

    return value;
  }

  private boolean isTranslationFunction(String value) {
    boolean isTranslation = false;
    if (value.trim().startsWith("_t('")
        || value.trim().startsWith("_t(‘")
        || value.trim().startsWith("_t(value:")) {
      isTranslation = true;
    }
    return isTranslation;
  }

  protected static ResourceBundle getResourceBundle(String language) {

    ResourceBundle bundle;

    if (language == null) {
      bundle = I18n.getBundle();
    } else if (language.equals("fr")) {
      bundle = I18n.getBundle(Locale.FRANCE);
    } else {
      bundle = I18n.getBundle(Locale.ENGLISH);
    }

    return bundle;
  }

  private Object getTranslatedValue(Object value) {

    if (value.toString().trim().startsWith("_t(value:")) {
      value = resourceBundle.getString("value:" + value.toString());
      value =
          value.toString().startsWith("value:") ? value.toString().replace("value:", "") : value;
    } else if (value.toString().trim().startsWith("_t('")
        || value.toString().trim().startsWith("_t(‘")) {
      value =
          value
              .toString()
              .trim()
              .replace("_t('", "")
              .replace("_t(‘", "")
              .replace("')", "")
              .replace("’)", "");
      value = resourceBundle.getString(value.toString());
    } else {
      value = resourceBundle.getString("value:" + value.toString());
      value =
          value.toString().startsWith("value:") ? value.toString().replace("value:", "") : value;
    }
    return value;
  }

  protected void setPictureRowShiftMap(String sheetName, String sheetType, int rowThreshold) {
    List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>
        pictureTripleList = pictureInputMap.get(sheetType);
    ClientAnchor anchor;

    if (ObjectUtils.isEmpty(pictureTripleList)) return;

    if (!sheetType.equalsIgnoreCase(TEMPLATE_SHEET_TITLE)) return;

    List<ImmutablePair<Integer, Integer>> pairList = new ArrayList<>();

    for (ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>> pictureTriple :
        pictureTripleList) {
      XSSFPicture picture = pictureTriple.getLeft();

      if (picture.getClientAnchor().getRow1() > rowThreshold) {
        anchor = pictureTriple.getLeft().getClientAnchor();
        pairList.add(new ImmutablePair<>(anchor.getRow1(), record));
      }
    }

    if (pictureRowShiftMap.containsKey(sheetName)) {
      if (pictureRowShiftMap.get(sheetName).containsKey(sheetType)) {
        pictureRowShiftMap.get(sheetName).get(sheetType).addAll(pairList);
      } else {
        pictureRowShiftMap.get(sheetName).put(sheetType, pairList);
      }

    } else {
      Map<String, List<ImmutablePair<Integer, Integer>>> newMap = new HashMap<>();
      newMap.put(sheetType, pairList);
      pictureRowShiftMap.put(sheetName, newMap);
    }
  }

  protected void shiftAll(Map<Integer, Map<String, Object>> map, int offset) {
    for (Map.Entry<Integer, Map<String, Object>> entry : map.entrySet()) {
      int rowNo = (int) entry.getValue().get(KEY_ROW);
      entry.getValue().replace(KEY_ROW, rowNo + offset);
    }
  }

  protected void shiftRows(Map<String, Object> map, boolean isBlankCell, int offset) {
    int rowIndex = (int) map.get(KEY_ROW);
    int columnIndex = (int) map.get(KEY_COLUMN);
    CellRangeAddress newAddress = null;
    CellRangeAddress oldAddress = null;
    if (rowIndex >= collectionEntryRow) {

      map.replace(KEY_ROW, rowIndex + offset);
      ImmutablePair<CellRangeAddress, CellRangeAddress> cellRangePair =
          cellMergingService.shiftMergedRegion(
              mergedCellsRangeAddressList, rowIndex, columnIndex, offset);
      if (ObjectUtils.isEmpty(cellRangePair)) return;
      oldAddress = cellRangePair.getLeft();
      newAddress = cellRangePair.getRight();

      if (ObjectUtils.notEmpty(newAddress)) {

        mergedCellsRangeAddressSetPerSheet.add(newAddress);

        // removes shifted blank merged region traces
        if (isBlankCell && blankMergedCellsRangeAddressSet.contains(oldAddress)) {
          mergedCellsRangeAddressSetPerSheet.remove(oldAddress);
        }
      }
    }
  }

  protected ImmutablePair<Integer, Map<Integer, Map<String, Object>>> getCollectionEntry(
      Map<Integer, Map<String, Object>> outputMap,
      Map<String, Object> entryValueMap,
      Collection<Object> collection,
      Map.Entry<Integer, Map<String, Object>> entry,
      Property property,
      String propertyName,
      int index,
      int totalRecord,
      boolean hide,
      String operationString,
      boolean translate)
      throws AxelorException, ScriptException {
    boolean isFirstIteration = true;
    ImmutablePair<Property, Object> pair;
    Mapper o2mMapper = Mapper.of(property.getTarget());
    propertyName = propertyName.substring(propertyName.indexOf(".") + 1);

    if (hide) {
      removeCellKeyList.add(entry.getKey());
    }

    Map<String, Object> newEntryValueMap = new HashMap<>(entryValueMap);
    this.shiftRows(newEntryValueMap, false, totalRecord);
    rowNumber = (int) newEntryValueMap.get(KEY_ROW);

    int localMergeOffset = 0;
    int rowOffset = 0;
    if (!collection.isEmpty()) {

      for (Object ob : collection) {
        Map<String, Object> newMap = new HashMap<>();

        pair = this.findField(o2mMapper, ob, propertyName);

        if (ObjectUtils.isEmpty(pair))
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              IExceptionMessage.NO_SUCH_FIELD + propertyName);

        property = pair.getLeft();
        newMap.putAll(newEntryValueMap);
        newMap.replace(KEY_ROW, rowNumber + rowOffset + localMergeOffset);
        ob = pair.getRight();

        Object keyValue = "";

        if (ObjectUtils.isEmpty(property.get(ob)) || hide) {
          keyValue = "";
        } else if (property.isReference()) {
          keyValue = findNameColumn(property, property.get(ob));
        } else if (!ObjectUtils.isEmpty(property.getSelection())) {
          String title =
              MetaStore.getSelectionItem(property.getSelection(), property.get(ob).toString())
                  .getTitle();
          keyValue = I18n.get(title);
        } else if (property.get(ob).getClass().equals(BigDecimal.class)) {
          keyValue = ((BigDecimal) property.get(ob)).setScale(BIGDECIMAL_SCALE).toString();
        } else {
          keyValue = property.get(ob).toString();
        }

        if (StringUtils.notEmpty(operationString)) {
          keyValue = calculateFromString(keyValue.toString().concat(operationString));
        }

        if (translate) {
          keyValue = getTranslatedValue(keyValue);
        }
        newMap.replace(KEY_VALUE, keyValue);

        while (outputMap.containsKey(index)) index++;
        if (isFirstIteration) {
          index = entry.getKey();
          isFirstIteration = false;
        }

        outputMap.put(index, newMap);
        index++;
        rowOffset = rowOffset + localMergeOffset + 1;
        if (localMergeOffset == 0 && mergeOffset != 0) localMergeOffset = mergeOffset;
      }
      if (record == 0) record = rowOffset - 1;
    } else {
      newEntryValueMap.replace(KEY_VALUE, "");
      outputMap.put(entry.getKey(), newEntryValueMap);
    }
    if (!nextRowCheckActive) nextRowCheckActive = true;

    return ImmutablePair.of(totalRecord, outputMap);
  }

  protected ImmutablePair<Integer, Map<Integer, Map<String, Object>>>
      getReportQueryBuilderCollectionEntry(
          Map<Integer, Map<String, Object>> outputMap,
          Map<String, Object> entryValueMap,
          Collection<Object> collection,
          Map.Entry<Integer, Map<String, Object>> entry,
          String key,
          int index,
          int totalRecord,
          boolean hide,
          String operationString,
          boolean translate)
          throws ScriptException {

    boolean isFirstIteration = true;

    if (hide) {
      removeCellKeyList.add(entry.getKey());
    }

    Map<String, Object> newEntryValueMap = new HashMap<>(entryValueMap);
    this.shiftRows(newEntryValueMap, false, totalRecord);
    rowNumber = (int) newEntryValueMap.get(KEY_ROW);

    int localMergeOffset = 0;
    int rowOffset = 0;
    if (!collection.isEmpty()) {

      for (Object ob : collection) {
        Map<String, Object> newMap = new HashMap<>();

        newMap.putAll(newEntryValueMap);
        newMap.replace(KEY_ROW, rowNumber + rowOffset + localMergeOffset);

        LinkedHashMap<String, String> recordMap = (LinkedHashMap<String, String>) ob;
        Object value = recordMap.get(key);
        Object keyValue = "";

        if (ObjectUtils.isEmpty(value) || hide) {
          keyValue = "";
        } else {
          keyValue = value;
        }

        if (StringUtils.notEmpty(operationString)) {
          keyValue = calculateFromString(keyValue.toString().concat(operationString));
        }

        if (translate) {
          keyValue = getTranslatedValue(keyValue);
        }
        newMap.replace(KEY_VALUE, keyValue);

        while (outputMap.containsKey(index)) index++;
        if (isFirstIteration) {
          index = entry.getKey();
          isFirstIteration = false;
        }

        outputMap.put(index, newMap);
        index++;
        rowOffset = rowOffset + localMergeOffset + 1;
        if (localMergeOffset == 0 && mergeOffset != 0) localMergeOffset = mergeOffset;
      }
      if (record == 0) record = rowOffset - 1;
    } else {

      newEntryValueMap.replace(KEY_VALUE, "");
      outputMap.put(entry.getKey(), newEntryValueMap);
    }
    if (!nextRowCheckActive) nextRowCheckActive = true;

    return ImmutablePair.of(totalRecord, outputMap);
  }

  protected void setMergedCellsRangeAddressSetPerSheet(
      Map<String, Object> entryValueMap, Collection<Object> collection, int totalRecord) {
    int firstRow = 0;
    int lastRow = 0;
    int firstColumn = 0;
    int lastColumn = 0;
    CellRangeAddress cellR = null;
    boolean isMatch = false;
    CellRangeAddress cellRangeOriginal;

    for (CellRangeAddress cellRange : mergedCellsRangeAddressList) {
      if (cellRange.isInRange(
          (int) entryValueMap.get(KEY_ROW), (int) entryValueMap.get(KEY_COLUMN))) {
        firstRow = cellRange.getFirstRow() + totalRecord;
        lastRow = cellRange.getLastRow() + totalRecord;
        firstColumn = cellRange.getFirstColumn();
        lastColumn = cellRange.getLastColumn();
        cellR = cellRange;
        isMatch = true;
      }
    }

    if (isMatch && cellR != null) {
      int mergeRowNumber = lastRow - firstRow + 1;
      for (int i = 0; i < collection.size(); i++) {

        cellRangeOriginal = new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn);
        mergedCellsRangeAddressSetPerSheet.add(cellRangeOriginal);
        firstRow += mergeRowNumber + ((mergeOffset + 1) - mergeRowNumber);
        lastRow += mergeRowNumber + ((mergeOffset + 1) - mergeRowNumber);
      }
    }
  }

  protected Property getProperty(Mapper mapper, String propertyName) {
    Property property;

    if (propertyName.contains(".")) {
      property = mapper.getProperty(propertyName.substring(0, propertyName.indexOf(".")));
    } else {
      property = mapper.getProperty(propertyName);
    }

    return property;
  }

  protected Map<String, Object> getFormulaResultMap(
      Map.Entry<Integer, Map<String, Object>> entry,
      String content,
      int totalRecord,
      long recordId,
      boolean hide) {
    Triple<String, String, String> operatingTriple = this.getOperatingTriple(content.substring(1));
    String operation = operatingTriple.getLeft();
    String m2oFieldName = operatingTriple.getMiddle();
    String condition = operatingTriple.getRight();
    CellRangeAddress newAddress = null;
    Map<String, Object> newMap = new HashMap<>();
    newMap.putAll(entry.getValue());
    newMap.replace(KEY_ROW, (Integer) newMap.get(KEY_ROW) + totalRecord);

    String result = "";
    if (!hide) {
      result = this.getResult(operation, m2oFieldName, condition, recordId);
    }

    newMap.replace(KEY_VALUE, result);

    newAddress =
        cellMergingService.setMergedCellsForTotalRow(
            mergedCellsRangeAddressList,
            (int) entry.getValue().get(KEY_ROW),
            (int) entry.getValue().get(KEY_COLUMN),
            totalRecord);
    if (ObjectUtils.notEmpty(newAddress)) {
      mergedCellsRangeAddressSetPerSheet.add(newAddress);
    }

    return newMap;
  }

  protected Map<String, Object> getNonCollectionEntry(
      Map<String, Object> m,
      Mapper mapper,
      Property property,
      Object object,
      String propertyName,
      int totalRecord,
      String operationString,
      boolean translate)
      throws AxelorException, ScriptException {
    ImmutablePair<Property, Object> pair = this.findField(mapper, object, propertyName);

    if (ObjectUtils.isEmpty(pair))
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          IExceptionMessage.NO_SUCH_FIELD + propertyName);

    property = pair.getLeft();
    object = pair.getRight();

    Object outputValue = "";
    if (object == null || ObjectUtils.isEmpty(property.get(object))) {
      outputValue = "";
    } else if (property.isReference()) {
      outputValue = findNameColumn(property, property.get(object));
    } else if (!ObjectUtils.isEmpty(property.getSelection())) {

      String title =
          MetaStore.getSelectionItem(property.getSelection(), property.get(object).toString())
              .getTitle();
      outputValue = I18n.get(title);

    } else if (property.get(object).getClass() == LocalDate.class) {
      LocalDate date = (LocalDate) property.get(object);
      String formattedDate = date.format(DateTimeFormatter.ISO_DATE);
      outputValue = formattedDate;
    } else {
      outputValue = property.get(object).toString();
    }

    if (StringUtils.notEmpty(operationString)) {
      outputValue = calculateFromString(outputValue.toString().concat(operationString));
    }

    if (translate) {
      outputValue = getTranslatedValue(outputValue);
    }
    m.replace(KEY_VALUE, outputValue);

    if (totalRecord > 0) this.shiftRows(m, false, totalRecord);

    return m;
  }

  protected String getResult(String operation, String content, String condition, long recordId) {

    Object resultObject = null;
    String o2mFieldName = content.substring(0, content.indexOf("."));
    String result = BigDecimal.ZERO.setScale(BIGDECIMAL_SCALE).toString();
    String propertyName = content.substring(content.indexOf(".") + 1);
    String queryString =
        String.format(
            "SELECT %s(l.%s) FROM %s s JOIN s.%s l where s.id = %s",
            operation, propertyName, modelName, o2mFieldName, recordId);

    if (StringUtils.notBlank(condition))
      queryString = queryString + " AND " + condition.replace(o2mFieldName, "l");

    Query query = JPA.em().createQuery(queryString);
    resultObject = query.getSingleResult();

    if (ObjectUtils.notEmpty(resultObject))
      result = ((BigDecimal) resultObject).setScale(BIGDECIMAL_SCALE).toString();

    return result;
  }

  protected Object findNameColumn(Property targetField, Object value) {
    String nameColumn = targetField.getTargetName();
    for (Property property : Mapper.of(targetField.getTarget()).getProperties()) {
      if (nameColumn.equals(property.getName())) {
        return property.get(value);
      }
    }
    return null;
  }

  protected ImmutablePair<Property, Object> findField(
      final Mapper mapper, Object value, String name) {
    final Iterator<String> iter = Splitter.on(".").split(name).iterator();
    Mapper current = mapper;
    Property property = current.getProperty(iter.next());

    if (property == null || (property.isJson() && iter.hasNext())) {
      return null;
    }

    while (property != null && property.getTarget() != null && iter.hasNext()) {
      if (ObjectUtils.notEmpty(value)) {
        value = property.get(value);
      }
      current = Mapper.of(property.getTarget());
      property = current.getProperty(iter.next());
    }

    return ImmutablePair.of(property, value);
  }

  protected XSSFSheet writeTemplateSheet(
      Map<Integer, Map<String, Object>> outputMap, XSSFSheet sheet, int offset) {
    sheet = this.write(outputMap, sheet, offset, false);
    this.fillMergedRegionCells(sheet);
    this.setMergedRegionsInSheet(sheet, mergedCellsRangeAddressSetPerSheet);
    return sheet;
  }

  protected void setMergedRegionsInSheet(
      XSSFSheet sheet, Set<CellRangeAddress> mergedCellsAddressSet) {
    if (ObjectUtils.isEmpty(mergedCellsAddressSet)) return;

    for (CellRangeAddress cellRange : mergedCellsAddressSet) sheet.addMergedRegionUnsafe(cellRange);
  }

  protected XSSFSheet write(
      Map<Integer, Map<String, Object>> outputMap,
      XSSFSheet sheet,
      int offset,
      boolean setExtraHeight) {
    for (Map.Entry<Integer, Map<String, Object>> entry : outputMap.entrySet()) {
      Map<String, Object> m = entry.getValue();
      int cellRow = (Integer) m.get(KEY_ROW) + offset;
      int cellColumn = (Integer) m.get(KEY_COLUMN);

      XSSFRow r = sheet.getRow(cellRow);
      if (r == null) {
        r = sheet.createRow(cellRow);
      }
      XSSFCell c = r.getCell(cellColumn);
      if (c == null) {
        c = r.createCell(cellColumn, CellType.STRING);
      }
      XSSFCellStyle newCellStyle = sheet.getWorkbook().createCellStyle();
      XSSFCellStyle oldCellStyle = (XSSFCellStyle) m.get(KEY_CELL_STYLE);

      Object cellValue = m.get(KEY_VALUE);
      if (cellValue.getClass().equals(XSSFRichTextString.class)) {
        c.setCellValue((XSSFRichTextString) cellValue);
      } else {
        c.setCellValue(cellValue.toString());
      }

      if (ObjectUtils.notEmpty(oldCellStyle)) {
        newCellStyle.cloneStyleFrom(oldCellStyle);
        c.setCellStyle(newCellStyle);
      }

      r.setHeightInPoints(setExtraHeight ? 50 : -1);
      sheet.setColumnWidth(cellColumn, originSheet.getColumnWidth(cellColumn));
    }
    return sheet;
  }

  protected void fillMergedRegionCells(XSSFSheet currentSheet) {
    XSSFCellStyle cellStyle;
    int firstRow;
    int lastRow;
    int firstColumn;
    int lastColumn;
    for (CellRangeAddress cellRange : mergedCellsRangeAddressSetPerSheet) {
      firstRow = cellRange.getFirstRow();
      lastRow = cellRange.getLastRow();
      firstColumn = cellRange.getFirstColumn();
      lastColumn = cellRange.getLastColumn();

      if (ObjectUtils.notEmpty(currentSheet.getRow(firstRow))
          && ObjectUtils.notEmpty(currentSheet.getRow(firstRow).getCell(firstColumn))) {
        cellStyle = currentSheet.getRow(firstRow).getCell(firstColumn).getCellStyle();
        for (int i = firstRow; i <= lastRow; i++) {
          for (int j = firstColumn; j <= lastColumn; j++) {
            if (ObjectUtils.isEmpty(currentSheet.getRow(i))) {
              currentSheet.createRow(i).createCell(j).setCellStyle(cellStyle);
            } else {
              if (ObjectUtils.isEmpty(currentSheet.getRow(i).getCell(j)))
                currentSheet.getRow(i).createCell(j).setCellStyle(cellStyle);
            }
          }
        }
      }
    }
  }

  protected XSSFSheet setHeader(
      XSSFSheet sheet,
      Map<Integer, Map<String, Object>> outputMap,
      Map<Integer, Map<String, Object>> headerOutputMap) {

    String html = print.getPrintPdfHeader();
    if (StringUtils.notEmpty(html)) {
      // convert html to rich text
      List<RichTextDetails> cellValues = new ArrayList<>();
      XSSFRichTextString cellValue = new XSSFRichTextString(html);
      cellValues.add(Beans.get(HtmlToExcel.class).createCellValue(html, sheet.getWorkbook()));
      if (ObjectUtils.notEmpty(cellValues.get(0))) {
        cellValue = Beans.get(HtmlToExcel.class).mergeTextDetails(cellValues);
      }
      // set rich text in map
      headerOutputMap.get(0).replace(KEY_VALUE, cellValue);
    }

    List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>
        headerTripleList = pictureInputMap.get(HEADER_SHEET_TITLE);

    int lastHeaderLineRow = 0;
    if (ObjectUtils.notEmpty(headerOutputMap)) {
      lastHeaderLineRow = this.getHeaderLines(headerOutputMap);
      sheet = this.write(headerOutputMap, sheet, 0, true);
    }

    if (ObjectUtils.notEmpty(headerTripleList)) {
      int lastPictureRow = this.getLastPictureRow(headerTripleList);
      int pictureOffset = lastPictureRow - lastHeaderLineRow;
      if (pictureOffset > 0) {
        lastHeaderLineRow += pictureOffset;
      }
      this.writePictures(sheet, headerTripleList, HEADER_SHEET_TITLE);
    }

    int offset = 2;
    if (outputMap.size() != 0) {
      offset = lastHeaderLineRow - (int) outputMap.get(0).get(KEY_ROW) + 2;
    }

    if (offset != 0) {
      this.shiftAll(outputMap, offset);
      cellMergingService.shiftMergedRegions(mergedCellsRangeAddressSetPerSheet, offset);
    }

    List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>
        templateTripleList = pictureInputMap.get(TEMPLATE_SHEET_TITLE);
    if (ObjectUtils.notEmpty(templateTripleList)) {
      this.setPictureRowOffset(
          templateTripleList, offset, sheet.getSheetName(), TEMPLATE_SHEET_TITLE);
      this.writePictures(sheet, templateTripleList, TEMPLATE_SHEET_TITLE);
    }

    this.setMergedRegionsInSheet(sheet, headerFooterMergedCellsMap.get(HEADER_SHEET_TITLE));

    return sheet;
  }

  protected int getLastPictureRow(
      List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>
          pictureTripleList) {
    int lastPictureRow = 0;
    for (ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>> pair :
        pictureTripleList) {
      if (lastPictureRow < pair.getLeft().getClientAnchor().getRow2())
        lastPictureRow = pair.getLeft().getClientAnchor().getRow2();
    }
    return lastPictureRow;
  }

  protected XSSFSheet setFooter(
      XSSFSheet sheet, Map<Integer, Map<String, Object>> footerOutputMap) {

    List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>
        footerTripleList = new ArrayList<>();
    if (ObjectUtils.notEmpty(pictureInputMap.get(FOOTER_SHEET_TITLE)))
      footerTripleList.addAll(pictureInputMap.get(FOOTER_SHEET_TITLE));

    int footerStartRow = this.getSheetLastRowNum(sheet, mergedCellsRangeAddressSetPerSheet) + 3;

    if (ObjectUtils.notEmpty(footerOutputMap)) {
      this.shiftAll(footerOutputMap, footerStartRow);
    }

    if (ObjectUtils.notEmpty(footerTripleList)) {
      this.setPictureRowOffset(
          footerTripleList, footerStartRow, sheet.getSheetName(), FOOTER_SHEET_TITLE);
      this.writePictures(sheet, footerTripleList, FOOTER_SHEET_TITLE);
    }

    sheet = this.write(footerOutputMap, sheet, 0, true);

    if (ObjectUtils.notEmpty(headerFooterMergedCellsMap.get(FOOTER_SHEET_TITLE))) {
      Set<CellRangeAddress> footerMergedCellsList =
          new HashSet<>(headerFooterMergedCellsMap.get(FOOTER_SHEET_TITLE));
      cellMergingService.shiftMergedRegions(footerMergedCellsList, footerStartRow);
      this.setMergedRegionsInSheet(sheet, footerMergedCellsList);
    }

    return sheet;
  }

  protected int getSheetLastRowNum(XSSFSheet sheet, Set<CellRangeAddress> mergedCellsSet) {
    int lastRowNum = 0;
    int temp = 0;
    lastRowNum = sheet.getLastRowNum();

    if (ObjectUtils.notEmpty(mergedCellsSet)) {
      for (CellRangeAddress cellRange : mergedCellsSet) {
        temp = cellRange.getLastRow();
        if (lastRowNum < temp) lastRowNum = temp;
      }
    }
    return lastRowNum;
  }

  protected void setPictureRowOffset(
      List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>
          pictureTripleList,
      int rowOffset,
      String sheetName,
      String sheetType) {
    ClientAnchor anchor;

    if (sheetType.equalsIgnoreCase(TEMPLATE_SHEET_TITLE)) {
      List<ImmutablePair<Integer, Integer>> pairList =
          pictureRowShiftMap.get(sheetName).get(sheetType);
      List<ImmutablePair<Integer, Integer>> newPairList = new ArrayList<>();
      for (ImmutablePair<Integer, Integer> pair : pairList) {
        newPairList.add(new ImmutablePair<>(pair.getLeft() + rowOffset, pair.getRight()));
      }

      pictureRowShiftMap.get(sheetName).replace(sheetType, newPairList);
    }

    for (ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>> triple :
        pictureTripleList) {
      anchor = triple.getLeft().getClientAnchor();
      anchor.setRow1(anchor.getRow1() + rowOffset);
      anchor.setRow2(anchor.getRow2() + rowOffset);
    }
  }

  protected Integer getHeaderLines(Map<Integer, Map<String, Object>> headerOutputMap) {
    int headerLines = 0;
    for (Map.Entry<Integer, Map<String, Object>> entry : headerOutputMap.entrySet()) {
      if (headerLines < (int) entry.getValue().get(KEY_ROW)) {
        headerLines = (int) entry.getValue().get(KEY_ROW);
      }
    }
    return headerLines + 1;
  }

  protected void getHeadersAndFooters(XSSFWorkbook wb) throws IOException, AxelorException {
    headerInputMap = this.getInputMap(wb, HEADER_SHEET_TITLE);
    footerInputMap = this.getInputMap(wb, FOOTER_SHEET_TITLE);
  }

  protected Object getCellValue(XSSFCell cell) {
    Object value = null;
    switch (cell.getCellType()) {
      case BOOLEAN:
        value = cell.getBooleanCellValue();
        break;
      case NUMERIC:
        value = cell.getNumericCellValue();
        break;
      case STRING:
        value = cell.getRichStringCellValue();
        break;
      case BLANK:
        value = cell.getStringCellValue();
        break;
      case ERROR:
        break;
      case FORMULA:
        break;
      case _NONE:
        break;
      default:
        break;
    }
    return value;
  }

  protected boolean isCellEmpty(XSSFCell cell) {
    BorderStyle borderStyleNone = BorderStyle.NONE;

    boolean isBlank = cell.getCellType().equals(CellType.BLANK);
    boolean isEmpty =
        (cell.getCellType() == CellType.STRING && StringUtils.isBlank(cell.getStringCellValue()))
            || cell.getCellType() == CellType.FORMULA;
    boolean hasNoBorder =
        cell.getCellStyle().getBorderLeft().equals(borderStyleNone)
            && cell.getCellStyle().getBorderRight().equals(borderStyleNone)
            && cell.getCellStyle().getBorderTop().equals(borderStyleNone)
            && cell.getCellStyle().getBorderBottom().equals(borderStyleNone);
    boolean hasNoBackgroundColor = cell.getCellStyle().getFillBackgroundColor() == 64;

    return isEmpty || (isBlank && hasNoBorder && hasNoBackgroundColor);
  }

  private short getCellFooterFontColor(String footerFontColor) {

    short color = IndexedColors.BLACK.getIndex();
    if (ObjectUtils.isEmpty(footerFontColor)) {
      return color;
    }
    switch (footerFontColor) {
      case "blue":
        color = IndexedColors.BLUE.getIndex();
        break;
      case "cyan":
        color = IndexedColors.LIGHT_BLUE.getIndex();
        break;
      case "dark-gray":
        color = IndexedColors.GREY_80_PERCENT.getIndex();
        break;
      case "gray":
        color = IndexedColors.GREY_50_PERCENT.getIndex();
        break;
      case "green":
        color = IndexedColors.GREEN.getIndex();
        break;
      case "light-gray":
        color = IndexedColors.GREY_25_PERCENT.getIndex();
        break;
      case "magneta":
        color = IndexedColors.LAVENDER.getIndex();
        break;
      case "orange":
        color = IndexedColors.ORANGE.getIndex();
        break;
      case "pink":
        color = IndexedColors.PINK.getIndex();
        break;
      case "red":
        color = IndexedColors.RED.getIndex();
        break;
      case "white":
        color = IndexedColors.WHITE.getIndex();
        break;
      case "yellow":
        color = IndexedColors.YELLOW.getIndex();
        break;
      default:
        break;
    }
    return color;
  }

  /** Groovy condition feature * */

  // for hide or show condition
  protected boolean getConditionResult(String statement, Object bean) {

    String conditionalText = statement.substring(statement.indexOf(":") + 1).trim();
    String condition =
        org.apache.commons.lang3.StringUtils.substringBetween(conditionalText, "(", ")");

    Object result = validateCondition(condition, bean);
    boolean flag = true;

    if (!(result instanceof Boolean)
        || (result instanceof Boolean
            && ((boolean) result == true && conditionalText.contains("show")
                || (boolean) result == false && conditionalText.contains("hide")))) {
      flag = false;
    }

    return flag;
  }

  // for if else condition
  protected ImmutablePair<String, String> getIfConditionResult(String statement, Object bean)
      throws IOException, AxelorException {

    String condition = "";
    Object flag;

    List<String> lines = IOUtils.readLines(new StringReader(statement));
    String resultValue = null;
    String operation = " ";
    String value = null;

    for (String line : lines) {
      if (line.startsWith("if") || line.startsWith("else if")) {
        condition = org.apache.commons.lang3.StringUtils.substringBetween(line, "(", ")").trim();
        flag = validateCondition(condition, bean);

        if ((boolean) flag) {
          resultValue = line.substring(line.indexOf("->") + 2).trim();
          break;
        }
      } else if (line.startsWith("else")) {
        resultValue = line.substring(line.indexOf("->") + 2).trim();
      }
    }

    if (StringUtils.isEmpty(resultValue)) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          "Invalid condition format : " + statement);
    } else if (resultValue.contains(" ") && resultValue.contains("$")) {
      value = resultValue.substring(0, resultValue.indexOf(" "));
      operation = resultValue.substring(resultValue.indexOf(" "));
    } else {
      value = resultValue;
    }

    return new ImmutablePair<>(value, operation.trim());
  }

  // calculates arithmetic operations using javascript engine
  protected String calculateFromString(String expression) throws ScriptException {
    if (StringUtils.isEmpty(expression)) return "";

    ScriptEngineManager scriptManager = new ScriptEngineManager();
    ScriptEngine eng = scriptManager.getEngineByName("JavaScript");

    return BigDecimal.valueOf((Double) eng.eval(expression)).setScale(BIGDECIMAL_SCALE).toString();
  }

  // solves groovy condition
  protected Object validateCondition(String condition, Object bean) {
    Context scriptContext = new Context(Mapper.toMap(bean), bean.getClass());
    ScriptHelper scriptHelper = new GroovyScriptHelper(scriptContext);

    if (ObjectUtils.isEmpty(condition)) {
      return "";
    }

    // replace all single quotes to groovy compatible quotes
    if (condition.contains("‘") || condition.contains("’")) {
      condition = condition.replaceAll("‘", "'").replaceAll("’", "'");
    }

    return scriptHelper.eval(condition);
  }
}
