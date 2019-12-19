package org.jxls;

import java.io.File;
import java.io.IOException;

import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Class that encapsulates POI for testing Excel file contents.
 */
public class TestWorkbook implements AutoCloseable {
    private Workbook workbook;
    private Sheet sheet;
    
    public TestWorkbook(File file) {
        try {
            workbook = WorkbookFactory.create(file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Following operations operate on the given sheet
     * @param name exact visible name
     */
    public void selectSheet(String name) {
        sheet = workbook.getSheet(name);
    }

    /**
     * Following operations operate on the given sheet
     * @param index starts with 0
     */
    public void selectSheet(int index) {
        sheet = workbook.getSheetAt(index);
    }

    /**
     * Expects text cell and returns its content.
     * @param row starts with 1
     * @param column 1 = A
     * @return String
     */
    public String getCellValueAsString(int row, int column) {
        return sheet.getRow(row - 1).getCell(column - 1).getStringCellValue();
    }

    /**
     * Expects (possibly formatted) text cell and returns its content.
     * @param row starts with 1
     * @param column 1 = A
     * @return RichTextString
     */
    public RichTextString getCellValueAsRichString(int row, int column) {
        return sheet.getRow(row - 1).getCell(column - 1).getRichStringCellValue();
    }

    /**
     * Expects numeric cell and returns its double value.
     * @param row starts with 1
     * @param column 1 = A
     * @return Double
     */
    public Double getCellValueAsDouble(int row, int column) {
        return Double.valueOf(sheet.getRow(row - 1).getCell(column - 1).getNumericCellValue());
    }
    
    /**
     * @param row starts with 1
     * @return row height in Twips
     */
    public short getRowHeight(int row) {
        return sheet.getRow(row - 1).getHeight();
    }
    
    public SheetConditionalFormatting getSheetConditionalFormatting() {
        return sheet.getSheetConditionalFormatting();
    }

    @Override
    public void close() {
        if (workbook != null) {
            try {
                workbook.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
