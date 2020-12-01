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

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.Print;
import com.axelor.apps.base.service.PrintService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.itextpdf.awt.geom.Dimension;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPCellEvent;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder.BorderSide;

public class ExcelToPdf {

  private static boolean potrait = true;
  private List<CellRangeAddress> mergedCellsList;
  private Header headerEvent;
  private Map<
          String, List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>>
      pdfPictureMap;
  private Map<String, Map<String, List<ImmutablePair<Integer, Integer>>>> pdfPictureRowShiftMap =
      new HashMap<>();
  private int pageNo = 1;
  private float[] headerFooterMarginArray = new float[2];
  private boolean pictureCell = false;
  private List<Image> imageList;
  private float dataSizeReductionPercentage = 100; // value should be between 0 to 100

  public class Header extends PdfPageEventHelper {

    protected Print print;
    protected PdfImportedPage page;

    public void setPrint(Print print) {
      this.print = print;
    }

    public void setPage(PdfImportedPage page) {
      this.page = page;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {

      PdfContentByte contentByte = writer.getDirectContent();

      try {

        if (ObjectUtils.notEmpty(page)) {
          contentByte.addTemplate(page, 0, 0);
        }

        if (ObjectUtils.notEmpty(print.getPrintPdfFooter())) {

          String footerTextAlignment = print.getFooterTextAlignment();
          String footerFontColor = print.getFooterFontColor();

          PdfPTable footerTable = new PdfPTable(1);

          Paragraph paragraph = new Paragraph(print.getPrintPdfFooter());
          Font font =
              FontFactory.getFont(
                  print.getFooterFontType() != null
                      ? print.getFooterFontType()
                      : FontFactory.TIMES_ROMAN);

          if (footerFontColor != null) {
            font.setColor(getCellFooterFontColor(footerFontColor));
          }
          font.setSize(
              print.getFooterFontSize().compareTo(BigDecimal.ZERO) > 0
                  ? print.getFooterFontSize().floatValue()
                  : 10);
          if (print.getIsFooterUnderLine()) {
            font.setStyle(Font.UNDERLINE);
          }

          PdfPCell cell = new PdfPCell();

          paragraph.setFont(font);

          cell.setBorder(Rectangle.NO_BORDER);
          if (footerTextAlignment != null) {
            paragraph.setAlignment(getCellFooterTextAlignment(footerTextAlignment));
          }

          cell.addElement(paragraph);

          footerTable.addCell(cell);
          footerTable.setTotalWidth(document.right() - document.left() - 60);
          footerTable.writeSelectedRows(0, 1, document.left(), document.bottom(), contentByte);
        }

        for (Image image : imageList) writer.getDirectContent().addImage(image);

      } catch (Exception e) {
        TraceBackService.trace(e);
      }

      ColumnText.showTextAligned(
          contentByte,
          Element.ALIGN_CENTER,
          new Phrase("Page " + pageNo),
          document.right() - 30,
          20,
          0);

      pageNo++;
    }

    private int getCellFooterTextAlignment(String footerTextAlignment) {

      int alignment = 0;
      switch (footerTextAlignment) {
        case "left":
          alignment = Element.ALIGN_LEFT;
          break;
        case "center":
          alignment = Element.ALIGN_CENTER;
          break;
        case "right":
          alignment = Element.ALIGN_RIGHT;
          break;
        default:
          break;
      }
      return alignment;
    }

    private BaseColor getCellFooterFontColor(String footerFontColor) {

      BaseColor color = BaseColor.BLACK;

      switch (footerFontColor) {
        case "blue":
          color = BaseColor.BLUE;
          break;
        case "cyan":
          color = BaseColor.CYAN;
          break;
        case "dark-gray":
          color = BaseColor.DARK_GRAY;
          break;
        case "gray":
          color = BaseColor.GRAY;
          break;
        case "green":
          color = BaseColor.GREEN;
          break;
        case "light-gray":
          color = BaseColor.LIGHT_GRAY;
          break;
        case "magneta":
          color = BaseColor.MAGENTA;
          break;
        case "orange":
          color = BaseColor.ORANGE;
          break;
        case "pink":
          color = BaseColor.PINK;
          break;
        case "red":
          color = BaseColor.RED;
          break;
        case "white":
          color = BaseColor.WHITE;
          break;
        case "yellow":
          color = BaseColor.YELLOW;
          break;
        default:
          break;
      }
      return color;
    }
  }

  class CellImage implements PdfPCellEvent {

    protected Image image;

    public CellImage(Image image) {
      this.image = image;
    }

    public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
      image.setAbsolutePosition(
          position.getLeft() + (cell.getBorderWidthLeft() * 3),
          position.getBottom() + (cell.getBorderWidthBottom() * 3));
      imageList.add(image);
    }
  }

  public File createPdfFromExcel(
      File excelFile,
      Map<String, ImmutablePair<XSSFSheet, XSSFSheet>> headerFooterSheetMap,
      Map<String, List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>>
          pictureMap,
      Map<String, Map<String, List<ImmutablePair<Integer, Integer>>>> pictureRowShiftMap,
      boolean isPotrait,
      BigDecimal dataSizeReduction,
      Print print)
      throws Exception {

    potrait = isPotrait;
    File pdfFile =
        MetaFiles.createTempFile(FilenameUtils.removeExtension(excelFile.getName()), ".pdf")
            .toFile();
    FileInputStream excelStream = new FileInputStream(excelFile);
    XSSFWorkbook workbook = new XSSFWorkbook(excelStream);
    pdfPictureMap = pictureMap;
    pdfPictureRowShiftMap = pictureRowShiftMap;
    this.setMaxMarginRowsArray(print);
    ImmutablePair<Document, File> pdfPair = createPdfDoc(pdfFile, print);
    Document pdfDoc = pdfPair.getLeft();
    pdfFile = pdfPair.getRight();
    dataSizeReductionPercentage = dataSizeReduction.floatValue();

    headerEvent.setPrint(print);

    for (int sheetNum = 0; sheetNum < workbook.getNumberOfSheets(); sheetNum++) {
      XSSFSheet sheet = workbook.getSheetAt(sheetNum);
      imageList = new ArrayList<>();
      sheetToPdf(pdfDoc, sheet);
      pdfDoc.newPage();
    }

    pdfDoc.close();
    workbook.close();
    excelStream.close();

    return pdfFile;
  }

  private void sheetToPdf(Document pdfDoc, XSSFSheet sheet) throws DocumentException, IOException {

    PdfPTable pdfPTable = this.createPdfPTable(sheet);
    pdfDoc.add(pdfPTable);
  }

  protected PdfPTable createPdfPTable(XSSFSheet sheet) throws DocumentException, IOException {

    if (ObjectUtils.isEmpty(sheet)) return null;

    mergedCellsList = sheet.getMergedRegions();
    int[] maxRowColumn = getMaxRowColumn(sheet);
    int sheetMaxRow = maxRowColumn[0];
    int sheetMaxCol = maxRowColumn[1];

    PdfPTable pdfPTable = new PdfPTable(sheetMaxCol);
    pdfPTable.setHorizontalAlignment(Element.ALIGN_LEFT);
    pdfPTable.setWidths(getColumnWidth(sheet, sheetMaxCol));
    pdfPTable.setWidthPercentage(100);
    int rowNum = 0;
    int lastValid = 0;

    while (rowNum < sheetMaxRow) {
      XSSFRow row = sheet.getRow(rowNum);
      rowNum++;
      if (row != null && row.getZeroHeight() || row == null) {
        continue;
      }

      int cellNum = 0;
      while (cellNum < pdfPTable.getNumberOfColumns()) {
        XSSFCell cell = row.getCell(cellNum);
        cellNum++;
        PdfPCell pdfPCell = null;

        if (cell != null) {

          pdfPCell = createPdfCell(row, cell, pdfPCell);
          if (pdfPCell == null) continue;
          if (pdfPCell.getPhrase() != null) {
            lastValid = rowNum;
          }
        } else {
          pdfPCell = createEmptyPdfCell();
        }
        pdfPTable.addCell(pdfPCell);
      }

      pdfPTable.completeRow();
    }

    for (int r = lastValid; r < rowNum; r++) {
      pdfPTable.deleteRow(lastValid);
    }

    pdfPTable.setComplete(true);
    return pdfPTable;
  }

  private PdfPCell createPdfCell(XSSFRow row, XSSFCell cell, PdfPCell pdfPCell)
      throws DocumentException, IOException {
    Font font = getFontStyle(cell.getCellStyle());

    // font reduction
    font.setSize((dataSizeReductionPercentage * font.getSize()) / 100);

    ImmutablePair<Integer, Integer> spanPair = getRowAndColumnSpan(cell);

    switch (cell.getCellType()) {
      case BLANK:
        if (isInMergedRegion(cell)) {

          return null;
        }

        pdfPCell = new PdfPCell();
        break;

      case STRING:
        String cellValue = cell.getStringCellValue();

        if (isInMergedRegion(cell) && StringUtils.isEmpty(cellValue)) {
          return null;
        }

        pdfPCell = new PdfPCell(getParagraph(cell, font));

        if (pictureCell) {
          this.setPicture(
              pdfPCell, cell.getRowIndex(), cell.getColumnIndex(), cell.getSheet().getSheetName());
          pictureCell = false;
        }
        break;

      default:
        return createEmptyPdfCell();
    }

    // cell height reduction
    float minimumHeight =
        (dataSizeReductionPercentage * (row.getHeightInPoints() * spanPair.getLeft())) / 100;

    pdfPCell.setMinimumHeight(minimumHeight);
    pdfPCell.setColspan(spanPair.getRight());
    pdfPCell.setRowspan(spanPair.getLeft());
    pdfPCell.setNoWrap(!cell.getCellStyle().getWrapText());
    pdfPCell.setHorizontalAlignment(cell.getCellStyle().getAlignment().ordinal() - 1);
    pdfPCell.setVerticalAlignment(cell.getCellStyle().getVerticalAlignment().ordinal());
    setCellBackGround(cell.getCellStyle(), pdfPCell);
    setCellBorder(pdfPCell, cell);

    return pdfPCell;
  }

  protected void setPicture(PdfPCell pdfPCell, int rowIndex, int columnIndex, String sheetName)
      throws BadElementException, IOException {
    String sheetType = "";
    if (sheetName.contains("Header")) {
      sheetType = "Header";
    } else if (sheetName.contains("Footer")) {
      sheetType = "Footer";
    } else {
      sheetType = "Template";
    }

    List<ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>>>
        pictureTripleList = pdfPictureMap.get(sheetType);
    if (ObjectUtils.isEmpty(pictureTripleList)) return;

    XSSFPicture picture;
    for (ImmutableTriple<XSSFPicture, Dimension, ImmutablePair<Integer, Integer>> pair :
        pictureTripleList) {
      picture = pair.getLeft();

      Image image = null;

      int pictureFirstRow = picture.getClientAnchor().getRow1();

      if (sheetType.equalsIgnoreCase("Template")) {
        List<ImmutablePair<Integer, Integer>> pairList =
            pdfPictureRowShiftMap.get(sheetName).get(sheetType);

        for (ImmutablePair<Integer, Integer> rowOffsetPair : pairList) {
          if (rowOffsetPair.getLeft() == pictureFirstRow) {
            pictureFirstRow += rowOffsetPair.getRight();
            break;
          }
        }
      }

      if (pictureFirstRow == rowIndex && picture.getClientAnchor().getCol1() == columnIndex) {
        image = Image.getInstance(picture.getPictureData().getData());

        // image size reduction
        float pictureWidth = ((float) pair.getMiddle().getWidth() * 50) / 100;
        float pictureHeight = ((float) pair.getMiddle().getHeight() * 50) / 100;

        image.scaleAbsolute(pictureWidth, pictureHeight);
        pdfPCell.setCellEvent(new CellImage(image));

        return;
      }
    }
  }

  private PdfPCell createEmptyPdfCell() {
    PdfPCell pdfPCell;
    pdfPCell = new PdfPCell();
    pdfPCell.setBorder(0);
    return pdfPCell;
  }

  private ImmutablePair<Document, File> createPdfDoc(File pdfFile, Print print)
      throws DocumentException, IOException {

    Document doc = null;

    if (potrait) {
      doc = new Document(PageSize.A4);
    } else {
      doc = new Document(PageSize.A4.rotate());
    }

    FileOutputStream fileOutputStream = new FileOutputStream(pdfFile.getAbsolutePath());
    PdfWriter writer = PdfWriter.getInstance(doc, fileOutputStream);

    headerEvent = new Header();
    writer.setPageEvent(headerEvent);

    // sets header page to event
    this.setPdfImportedPageHeader(print, writer);
    doc.setMargins(20, 20, headerFooterMarginArray[0], headerFooterMarginArray[1]);
    doc.setMarginMirroring(false);
    doc.open();

    return new ImmutablePair<>(doc, pdfFile);
  }

  private int[] getMaxRowColumn(XSSFSheet sheet) {

    Iterator<Row> rowIter = sheet.iterator();
    int[] maxRowColumn = new int[2];
    int maxCol = 0;
    int maxRow = 0;
    while (rowIter.hasNext()) {
      Row row = rowIter.next();
      Iterator<Cell> cellIter = row.cellIterator();
      while (cellIter.hasNext()) {
        Cell cell = cellIter.next();
        if (cell.getCellType() != CellType.BLANK) {
          int colIndex = cell.getColumnIndex();
          if (maxCol <= colIndex) {
            maxCol = colIndex + 1;
          }
          int rowIndex = row.getRowNum();
          if (maxRow <= rowIndex) {
            maxRow = rowIndex + 1;
          }
        }
      }
    }
    maxRowColumn[0] = maxRow;
    maxRowColumn[1] = maxCol + 1;
    return maxRowColumn;
  }

  private Font getFontStyle(XSSFCellStyle cellStyle) {

    XSSFFont xssfFont = cellStyle.getFont();
    String fontFamily = FontFactory.HELVETICA;
    if (FontFactory.isRegistered(xssfFont.getFontName())) {
      fontFamily = xssfFont.getFontName();
    }
    Font font = FontFactory.getFont(fontFamily);
    int fontStyle = Font.NORMAL;

    if (xssfFont.getBold()) {
      fontStyle |= Font.BOLD;
    }
    if (xssfFont.getItalic()) {
      fontStyle |= Font.ITALIC;
    }
    if (xssfFont.getUnderline() == 1) {
      fontStyle |= Font.UNDERLINE;
    }

    font.setSize((float) xssfFont.getFontHeight() / 20);
    font.setStyle(fontStyle);
    font.setColor(getFontColor(cellStyle));

    return font;
  }

  private float[] getColumnWidth(XSSFSheet sheet, int maxCol) {

    float[] cells = new float[maxCol];
    int cellNum = 0;
    while (cellNum < maxCol) {
      cells[cellNum] = (float) sheet.getColumnWidth(cellNum);
      cellNum++;
    }
    return cells;
  }

  private void setCellBorder(PdfPCell pdfPCell, XSSFCell cell) {

    int cellBorder = Rectangle.NO_BORDER;

    if (cell.getCellStyle().getBorderLeft().ordinal() != 0) {
      cellBorder |= Rectangle.LEFT;
      pdfPCell.setBorderColorLeft(getBorderColor(cell.getCellStyle(), BorderSide.LEFT));
      pdfPCell.setBorderWidthLeft((float) 0.5);
    }
    if (cell.getCellStyle().getBorderRight().ordinal() != 0) {
      cellBorder |= Rectangle.RIGHT;
      pdfPCell.setBorderColorRight(getBorderColor(cell.getCellStyle(), BorderSide.RIGHT));
      pdfPCell.setBorderWidthRight((float) 0.5);
    }
    if (cell.getCellStyle().getBorderTop().ordinal() != 0) {
      cellBorder |= Rectangle.TOP;
      pdfPCell.setBorderColorTop(getBorderColor(cell.getCellStyle(), BorderSide.TOP));
      pdfPCell.setBorderWidthTop((float) 0.5);
    }
    if (cell.getCellStyle().getBorderBottom().ordinal() != 0) {
      cellBorder |= Rectangle.BOTTOM;
      pdfPCell.setBorderColorBottom(getBorderColor(cell.getCellStyle(), BorderSide.BOTTOM));
      pdfPCell.setBorderWidthBottom((float) 0.5);
    }

    pdfPCell.setBorder(cellBorder);
  }

  private BaseColor getBorderColor(XSSFCellStyle cellStyle, BorderSide side) {

    XSSFColor color = cellStyle.getBorderColor(side);
    if (color == null || ObjectUtils.isEmpty(color.getARGBHex())) {
      return null;
    }

    String argbCode = color.getARGBHex();
    String hexColor = "#" + argbCode.substring(2);

    return getBaseColor(hexColor);
  }

  private BaseColor getFontColor(XSSFCellStyle cellStyle) {

    XSSFFont hssfFont = cellStyle.getFont();
    if (hssfFont.getXSSFColor() == null) {
      return null;
    }

    String argbCode = hssfFont.getXSSFColor().getARGBHex();
    String hexColor = "#" + argbCode.substring(2);

    return getBaseColor(hexColor);
  }

  private void setCellBackGround(XSSFCellStyle cellStyle, PdfPCell pdfPCell) {

    if (cellStyle.getFillForegroundColorColor() != null) {
      String argbCode = cellStyle.getFillForegroundColorColor().getARGBHex();
      String hexColor = "#" + argbCode.substring(2);
      pdfPCell.setBackgroundColor(getBaseColor(hexColor));
    }
  }

  private BaseColor getBaseColor(String hexColor) {
    int r = Integer.valueOf(hexColor.substring(1, 3), 16);
    int g = Integer.valueOf(hexColor.substring(3, 5), 16);
    int b = Integer.valueOf(hexColor.substring(5, 7), 16);
    return new BaseColor(r, g, b);
  }

  private Paragraph getParagraph(XSSFCell cell, Font font) {
    Paragraph p = new Paragraph();
    String pdfValue = cell.getStringCellValue();

    int runIndex = 0;
    int runLength = 0;
    boolean hasScript = false;
    XSSFCellStyle style = null;
    XSSFFont rtsFont = null;
    XSSFRichTextString rts = (XSSFRichTextString) cell.getRichStringCellValue();
    style = cell.getCellStyle();
    rtsFont = style.getFont();

    ImmutablePair<String, String> superTagPair = new ImmutablePair<>("<super>", "</super>");
    ImmutablePair<String, String> subTagPair = new ImmutablePair<>("<sub>", "</sub>");

    if (rts.numFormattingRuns() > 1) {
      for (int k = 0; k < rts.numFormattingRuns(); k++) {

        runLength = rts.getLengthOfFormattingRun(k);
        runIndex = rts.getIndexOfFormattingRun(k);
        String scriptText = rts.toString().substring(runIndex, (runIndex + runLength));
        rtsFont = rts.getFontOfFormattingRun(k);

        if (rtsFont.getTypeOffset() == XSSFFont.SS_SUPER) {
          hasScript = true;
          pdfValue =
              pdfValue.replace(
                  scriptText, superTagPair.getLeft() + scriptText + superTagPair.getRight());
        }
        if (rtsFont.getTypeOffset() == XSSFFont.SS_SUB) {
          hasScript = true;
          pdfValue =
              pdfValue.replace(
                  scriptText, subTagPair.getLeft() + scriptText + subTagPair.getRight());
        }
      }
    }

    if (hasScript) {
      p.addAll(getChunkList(pdfValue, font, superTagPair, subTagPair));
    } else {
      p.add(new Chunk(pdfValue, font));
    }
    return p;
  }

  private List<Chunk> getChunkList(
      String pdfValue,
      Font font,
      ImmutablePair<String, String> superTagPair,
      ImmutablePair<String, String> subTagPair) {
    List<Chunk> chunkList = new ArrayList<>();
    Font scriptFont = new Font(font);
    scriptFont.setSize(font.getSize() - 3);

    while ((pdfValue.contains(superTagPair.getLeft()) && pdfValue.contains(superTagPair.getRight()))
        || (pdfValue.contains(subTagPair.getLeft()) && pdfValue.contains(subTagPair.getRight()))) {

      if (pdfValue.contains(superTagPair.getLeft()) && pdfValue.contains(superTagPair.getRight())) {
        chunkList.add(
            new Chunk(
                org.apache.commons.lang3.StringUtils.substringBefore(
                    pdfValue, superTagPair.getLeft()),
                font));
        Chunk chunk =
            new Chunk(
                org.apache.commons.lang3.StringUtils.substringBetween(
                    pdfValue, superTagPair.getLeft(), superTagPair.getRight()),
                scriptFont);
        chunk.setTextRise(font.getSize() / 3);
        chunkList.add(chunk);
        pdfValue =
            org.apache.commons.lang3.StringUtils.substringAfter(pdfValue, superTagPair.getRight());
      }

      if (pdfValue.contains(subTagPair.getLeft()) && pdfValue.contains(subTagPair.getRight())) {
        chunkList.add(
            new Chunk(
                org.apache.commons.lang3.StringUtils.substringBefore(
                    pdfValue, subTagPair.getLeft()),
                font));
        Chunk chunk =
            new Chunk(
                org.apache.commons.lang3.StringUtils.substringBetween(
                    pdfValue, subTagPair.getLeft(), subTagPair.getRight()),
                scriptFont);
        chunk.setTextRise(-(font.getSize() / 3));
        chunkList.add(chunk);
        pdfValue =
            org.apache.commons.lang3.StringUtils.substringAfter(pdfValue, subTagPair.getRight());
      }
    }

    chunkList.add(new Chunk(pdfValue, font));

    return chunkList;
  }

  private ImmutablePair<Integer, Integer> getRowAndColumnSpan(XSSFCell cell) {
    int colSpan = 0;
    int rowSpan = 1;
    if (ObjectUtils.notEmpty(mergedCellsList)) {
      for (CellRangeAddress cellRange : mergedCellsList) {

        if (cellRange.isInRange(cell)) {
          rowSpan = cellRange.getLastRow() - cellRange.getFirstRow() + 1;
          colSpan = cellRange.getLastColumn() - cellRange.getFirstColumn() + 1;
          break;
        }
      }
    }
    return new ImmutablePair<>(rowSpan, colSpan);
  }

  private boolean isInMergedRegion(XSSFCell cell) {
    boolean isInMergedRegion = false;

    if (ObjectUtils.notEmpty(mergedCellsList)) {
      for (CellRangeAddress cellRange : mergedCellsList) {
        if (cellRange.isInRange(cell)) {
          if (cell.getRowIndex() == cellRange.getFirstRow()
              && cell.getColumnIndex() == cellRange.getFirstColumn()) {

            pictureCell = true;
            break;
          } else {
            isInMergedRegion = true;
            break;
          }
        }
      }
    }
    return isInMergedRegion;
  }

  protected void setMaxMarginRowsArray(Print print) {

    float headerMargin = 0;
    float footerMargin = 0;

    if (StringUtils.notEmpty(print.getPrintPdfHeader())) {
      headerMargin = 105;
    }

    if (StringUtils.notEmpty(print.getPrintPdfFooter())) {
      footerMargin = print.getFooterFontSize().floatValue() * 3;
    }

    headerFooterMarginArray[0] = headerMargin + 20;
    headerFooterMarginArray[1] = footerMargin + 20;
  }

  protected void setPdfImportedPageHeader(Print print, PdfWriter writer) throws IOException {

    String attachmentPath = AppSettings.get().getPath("file.upload.dir", "");
    if (attachmentPath != null) {
      attachmentPath =
          attachmentPath.endsWith(File.separator)
              ? attachmentPath
              : attachmentPath + File.separator;
    }

    // creates pdf file from html header
    File headerFile = MetaFiles.createTempFile("Header", ".pdf").toFile();
    FileOutputStream headerFileOutputStream = new FileOutputStream(headerFile.getAbsolutePath());
    com.itextpdf.kernel.pdf.PdfWriter pdfWriter =
        new com.itextpdf.kernel.pdf.PdfWriter(headerFileOutputStream);
    PdfDocument pdfDoc = new PdfDocument(pdfWriter);
    pdfDoc.setDefaultPageSize(
        potrait
            ? com.itextpdf.kernel.geom.PageSize.A4
            : com.itextpdf.kernel.geom.PageSize.A4.rotate());

    String html = Beans.get(PrintService.class).generateHtml(print);
    ConverterProperties converterProperties = new ConverterProperties();
    converterProperties.setBaseUri(attachmentPath);
    HtmlConverter.convertToPdf(html, pdfDoc, converterProperties);

    headerFileOutputStream.close();

    // reads header from already created pdf file
    FileInputStream headerInputStream = new FileInputStream(headerFile.getAbsolutePath());
    PdfReader reader = new PdfReader(headerInputStream);
    PdfImportedPage page = writer.getImportedPage(reader, 1);
    headerInputStream.close();

    // sets header page to the event
    headerEvent.setPage(page);

    // delete header pdf file
    headerFile.delete();
  }
}
