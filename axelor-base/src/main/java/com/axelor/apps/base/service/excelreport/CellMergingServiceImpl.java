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

import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;

public class CellMergingServiceImpl implements CellMergingService {

  private static final String KEY_ROW = "Row";
  private static final String KEY_COLUMN = "Column";
  private static final String KEY_VALUE = "Value";
  private static final String KEY_CELL_STYLE = "CellStyle";

  @Override // sets global variable mergeOffset
  public int setMergeOffset(
      Map<Integer, Map<String, Object>> inputMap,
      Mapper mapper,
      List<CellRangeAddress> mergedCellsRangeAddressList) {
    int mergeRowNumber;
    int lastRow, firstRow;
    Property property;
    String content = "";
    int mergeOffset = 0;
    for (Map.Entry<Integer, Map<String, Object>> entry : inputMap.entrySet()) {
      for (CellRangeAddress cellRange : mergedCellsRangeAddressList) {
        if (cellRange.isInRange(
            (int) entry.getValue().get(KEY_ROW), (int) entry.getValue().get(KEY_COLUMN))) {
          content = entry.getValue().get(KEY_VALUE).toString();
          if (StringUtils.notBlank(content)) {
            if (content.charAt(0) == '$') {
              String propertyName = content.substring(1);

              if (!content.contains("$formula")) {

                if (propertyName.contains(".")) {

                  property =
                      mapper.getProperty(propertyName.substring(0, propertyName.indexOf(".")));

                  if (property.isCollection()) {
                    firstRow = cellRange.getFirstRow();
                    lastRow = cellRange.getLastRow();
                    mergeRowNumber = lastRow - firstRow + 1;
                    if (mergeRowNumber > mergeOffset) mergeOffset = mergeRowNumber - 1;
                  }
                }
              }
            }
          }
        }
      }
    }
    return mergeOffset;
  }

  @Override // sets blank merged cells from origin sheet to target sheet (header and footer sheets
  // not included)
  public Set<CellRangeAddress> getBlankMergedCells(
      XSSFSheet originSheet, List<CellRangeAddress> mergedCellsRangeAddressList, String sheetType) {

    Set<CellRangeAddress> blankMergedCells = new HashSet<CellRangeAddress>();
    XSSFCell cell = null;
    for (CellRangeAddress cellRange : mergedCellsRangeAddressList) {
      cell = originSheet.getRow(cellRange.getFirstRow()).getCell(cellRange.getFirstColumn());

      if (ObjectUtils.notEmpty(cell) && ObjectUtils.isEmpty(cell.getStringCellValue())) {
        blankMergedCells.add(cellRange);
      }
    }
    return blankMergedCells;
  }

  @Override // sets merged cells for the result row of the current table
  public CellRangeAddress setMergedCellsForTotalRow(
      List<CellRangeAddress> mergedCellsRangeAddressList,
      int rowIndex,
      int columnIndex,
      int totalRecord) {
    CellRangeAddress cellRange =
        this.findMergedRegion(mergedCellsRangeAddressList, rowIndex, columnIndex);

    if (ObjectUtils.isEmpty(cellRange)) return null;

    int firstCellRow = cellRange.getFirstRow() + totalRecord;
    int lastCellRow = cellRange.getLastRow() + totalRecord;

    CellRangeAddress newAddress =
        new CellRangeAddress(
            firstCellRow, lastCellRow, cellRange.getFirstColumn(), cellRange.getLastColumn());

    return newAddress;
  }

  @Override // shifts only single merged region according to the given offset and returns both
  // original and offsetted merged region
  public ImmutablePair<CellRangeAddress, CellRangeAddress> shiftMergedRegion(
      List<CellRangeAddress> mergedCellsRangeAddressList,
      int rowIndex,
      int columnIndex,
      int offset) {
    CellRangeAddress originalCellRange =
        this.findMergedRegion(mergedCellsRangeAddressList, rowIndex, columnIndex);

    if (ObjectUtils.isEmpty(originalCellRange)) return null;

    int firstCellRow = originalCellRange.getFirstRow() + offset;
    int lastCellRow = originalCellRange.getLastRow() + offset;

    CellRangeAddress offsettedCellRange =
        new CellRangeAddress(
            firstCellRow,
            lastCellRow,
            originalCellRange.getFirstColumn(),
            originalCellRange.getLastColumn());

    return new ImmutablePair<CellRangeAddress, CellRangeAddress>(
        originalCellRange, offsettedCellRange);
  }

  protected CellRangeAddress findMergedRegion(
      List<CellRangeAddress> mergedCellsRangeAddressList, int rowIndex, int columnIndex) {
    CellRangeAddress mergedRegion = null;
    for (CellRangeAddress cellR : mergedCellsRangeAddressList) {
      if (cellR.isInRange(rowIndex, columnIndex)) {
        mergedRegion = cellR;
      }
    }
    return mergedRegion;
  }

  @Override // shifts all the merged regions according to the given offset
  public void shiftMergedRegions(Set<CellRangeAddress> mergedRegionsAddressSet, int offset) {
    if (ObjectUtils.isEmpty(mergedRegionsAddressSet)) return;

    Set<CellRangeAddress> newMergedRegionsSet = new HashSet<>();

    for (CellRangeAddress cellRange : mergedRegionsAddressSet) {
      int firstCellRow = cellRange.getFirstRow() + offset;
      int lastCellRow = cellRange.getLastRow() + offset;

      newMergedRegionsSet.add(
          new CellRangeAddress(
              firstCellRow, lastCellRow, cellRange.getFirstColumn(), cellRange.getLastColumn()));
    }

    mergedRegionsAddressSet.clear();
    mergedRegionsAddressSet.addAll(newMergedRegionsSet);
  }
}
