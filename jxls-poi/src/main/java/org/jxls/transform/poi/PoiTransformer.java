package org.jxls.transform.poi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.ConditionalFormatting;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jxls.common.AreaRef;
import org.jxls.common.CellData;
import org.jxls.common.CellRef;
import org.jxls.common.Context;
import org.jxls.common.JxlsException;
import org.jxls.common.PoiExceptionLogger;
import org.jxls.common.RowData;
import org.jxls.common.SheetData;
import org.jxls.common.Size;
import org.jxls.logging.JxlsLogger;
import org.jxls.transform.AbstractTransformer;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell;

/**
 * POI implementation of {@link org.jxls.transform.Transformer} interface
 *
 * @author Leonid Vysochyn
 */
public class PoiTransformer extends AbstractTransformer {
    private Workbook workbook;
    private OutputStream outputStream;
    private InputStream inputStream;
    private final boolean isSXSSF;
    private JxlsLogger logger = new PoiExceptionLogger();
    
    /**
     * @param workbook source workbook to transform
     * @param streaming false: without streaming, true: with streaming (with default parameter values)
     */
    public PoiTransformer(Workbook workbook, boolean streaming) {
        this(workbook, streaming, SXSSFWorkbook.DEFAULT_WINDOW_SIZE, false, false);
    }

    /**
     * @param workbook source workbook to transform
     * @param streaming flag to set if SXSSF stream support is enabled
     * @param rowAccessWindowSize only used if streaming is true
     * @param compressTmpFiles only used if streaming is true
     * @param useSharedStringsTable only used if streaming is true
     */
    public PoiTransformer(Workbook workbook, boolean streaming, int rowAccessWindowSize, boolean compressTmpFiles, boolean useSharedStringsTable) {
        this.workbook = workbook;
        isSXSSF = streaming;
        readCellData();
        if (isSXSSF) {
            if (this.workbook instanceof XSSFWorkbook) {
                XSSFWorkbook xwb  = (XSSFWorkbook)this.workbook;
                this.workbook = new SXSSFWorkbook(xwb, rowAccessWindowSize, compressTmpFiles, useSharedStringsTable);
            } else {
                throw new IllegalArgumentException("Failed to create POI Transformer using SXSSF API as the input workbook is not XSSFWorkbook");
            }
        }
    }
    
    protected boolean isStreaming() {
        return isSXSSF;
    }
    
    public void setInputStream(InputStream is) {
        inputStream = is;
    }

    @Override
    public boolean isForwardOnly() {
        return isStreaming();
    }

    public Workbook getWorkbook() {
        return workbook;
    }

    private void readCellData() {
        int numberOfSheets = workbook.getNumberOfSheets();
        for (int i = 0; i < numberOfSheets; i++) {
            Sheet sheet = workbook.getSheetAt(i);
            SheetData sheetData = PoiSheetData.createSheetData(sheet, this);
            sheetMap.put(sheetData.getSheetName(), sheetData);
        }
    }

    @Override
    public void transform(CellRef srcCellRef, CellRef targetCellRef, Context context, boolean updateRowHeightFlag) {
        CellData cellData = isTransformable(srcCellRef, targetCellRef);
        if (cellData == null) {
            return;
        }
        Sheet destSheet = workbook.getSheet(targetCellRef.getSheetName());
        if (destSheet == null) {
            destSheet = workbook.createSheet(targetCellRef.getSheetName());
            PoiUtil.copySheetProperties(workbook.getSheet(srcCellRef.getSheetName()), destSheet);
        }
        Row destRow = destSheet.getRow(targetCellRef.getRow());
        if (destRow == null) {
            destRow = destSheet.createRow(targetCellRef.getRow());
        }
        transformCell(srcCellRef, targetCellRef, context, updateRowHeightFlag, cellData, destSheet, destRow);
    }
    
    protected CellData isTransformable(CellRef srcCellRef, CellRef targetCellRef) {
        CellData cellData = getCellData(srcCellRef);
        if (cellData != null) {
            if (targetCellRef == null || targetCellRef.getSheetName() == null) {
                getLogger().info("targetCellRef is null or has empty sheet name, cellRef=" + targetCellRef);
                return null; // do not transform
            }
        }
        return cellData;
    }

    protected void transformCell(CellRef srcCellRef, CellRef targetCellRef, Context context,
            boolean updateRowHeightFlag, CellData cellData, Sheet destSheet, Row destRow) {
        SheetData sheetData = sheetMap.get(srcCellRef.getSheetName());
        if (!isIgnoreColumnProps()) {
            destSheet.setColumnWidth(targetCellRef.getCol(), sheetData.getColumnWidth(srcCellRef.getCol()));
        }
        if (updateRowHeightFlag && !isIgnoreRowProps()) {
            destRow.setHeight((short) sheetData.getRowData(srcCellRef.getRow()).getHeight());
        }
        org.apache.poi.ss.usermodel.Cell destCell = destRow.getCell(targetCellRef.getCol());
        if (destCell == null) {
            destCell = destRow.createCell(targetCellRef.getCol());
        }
        try {
            // conditional formatting
            destCell.setBlank();
            ((PoiCellData) cellData).writeToCell(destCell, context, this);
            copyMergedRegions(cellData, targetCellRef);
        } catch (Exception e) {
            getLogger().handleCellException(e, cellData.toString(), context);
        }
    }

    @Override
    public JxlsLogger getLogger() {
        if (logger == null) {
            throw new JxlsException("Transformer has no logger");
        }
        return logger;
    }
    
    @Override
    public void setLogger(JxlsLogger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("logger must not be null");
        }
        this.logger = logger;
    }

    @Override
    public void resetArea(AreaRef areaRef) {
        removeMergedRegions(areaRef);
        removeConditionalFormatting(areaRef);
    }

    private void removeMergedRegions(AreaRef areaRef) {
        Sheet destSheet = workbook.getSheet(areaRef.getSheetName());
        int numMergedRegions = destSheet.getNumMergedRegions();
        for (int i = numMergedRegions; i > 0; i--) {
            destSheet.removeMergedRegion(i - 1);
        }
    }

    // this method updates conditional formatting ranges only when the range is inside the passed areaRef
    private void removeConditionalFormatting(AreaRef areaRef) {
        Sheet destSheet = workbook.getSheet(areaRef.getSheetName());
        CellRangeAddress areaRange = CellRangeAddress.valueOf(areaRef.toString());
        SheetConditionalFormatting sheetConditionalFormatting = destSheet.getSheetConditionalFormatting();
        int numConditionalFormattings = sheetConditionalFormatting.getNumConditionalFormattings();
        for (int index = 0; index < numConditionalFormattings; index++) {
            ConditionalFormatting conditionalFormatting = sheetConditionalFormatting.getConditionalFormattingAt(index);
            CellRangeAddress[] ranges = conditionalFormatting.getFormattingRanges();
            List<CellRangeAddress> newRanges = new ArrayList<>();
            for (CellRangeAddress range : ranges) {
                if (!areaRange.isInRange(range.getFirstRow(), range.getFirstColumn()) || !areaRange.isInRange(range.getLastRow(), range.getLastColumn())) {
                    newRanges.add(range);
                }
            }
            conditionalFormatting.setFormattingRanges(newRanges.toArray(new CellRangeAddress[] {}));
        }
    }

    protected final void copyMergedRegions(CellData sourceCellData, CellRef destCell) {
        if (sourceCellData.getSheetName() == null) {
            throw new IllegalArgumentException("Sheet name is null in copyMergedRegions");
        }
        PoiSheetData sheetData = (PoiSheetData) sheetMap.get(sourceCellData.getSheetName());
        CellRangeAddress cellMergedRegion = null;
        for (CellRangeAddress mergedRegion : sheetData.getMergedRegions()) {
            if (mergedRegion.getFirstRow() == sourceCellData.getRow() && mergedRegion.getFirstColumn() == sourceCellData.getCol()) {
                cellMergedRegion = mergedRegion;
                break;
            }
        }
        if (cellMergedRegion != null) {
            findAndRemoveExistingCellRegion(destCell);
            Sheet destSheet = workbook.getSheet(destCell.getSheetName());
            destSheet.addMergedRegion(new CellRangeAddress(destCell.getRow(), destCell.getRow() + cellMergedRegion.getLastRow() - cellMergedRegion.getFirstRow(),
                    destCell.getCol(), destCell.getCol() + cellMergedRegion.getLastColumn() - cellMergedRegion.getFirstColumn()));
        }
    }

    protected final void findAndRemoveExistingCellRegion(CellRef cellRef) {
        Sheet destSheet = workbook.getSheet(cellRef.getSheetName());
        int numMergedRegions = destSheet.getNumMergedRegions();
        for (int i = 0; i < numMergedRegions; i++) {
            CellRangeAddress mergedRegion = destSheet.getMergedRegion(i);
            if (mergedRegion.getFirstRow() <= cellRef.getRow() && mergedRegion.getLastRow() >= cellRef.getRow() &&
                    mergedRegion.getFirstColumn() <= cellRef.getCol() && mergedRegion.getLastColumn() >= cellRef.getCol()) {
                destSheet.removeMergedRegion(i);
                break;
            }
        }
    }

    @Override
    public void setFormula(CellRef cellRef, String formulaString) {
        if (cellRef == null || cellRef.getSheetName() == null) return;
        Sheet sheet = workbook.getSheet(cellRef.getSheetName());
        if (sheet == null) {
            sheet = workbook.createSheet(cellRef.getSheetName());
        }
        Row row = sheet.getRow(cellRef.getRow());
        if (row == null) {
            row = sheet.createRow(cellRef.getRow());
        }
        org.apache.poi.ss.usermodel.Cell poiCell = row.getCell(cellRef.getCol());
        if (poiCell == null) {
            poiCell = row.createCell(cellRef.getCol());
        }
        try {
            poiCell.setCellFormula(formulaString);
            clearCellValue(poiCell);
        } catch (Exception e) {
            getLogger().handleFormulaException(e, cellRef.getCellName(), formulaString);
        }
    }
    
    // protected so any user can change this piece of code
    protected void clearCellValue(org.apache.poi.ss.usermodel.Cell poiCell) {
        if (poiCell instanceof XSSFCell) {
            CTCell cell = ((XSSFCell) poiCell).getCTCell(); // POI internal access, but there's no other way
            // Now do the XSSFCell.setFormula code that was done before POI commit https://github.com/apache/poi/commit/1253a29
            // After setting the formula in attribute f we clear the value attribute v if set. This causes a recalculation
            // and prevents wrong formula results.
            if (cell.isSetV()) {
                cell.unsetV();
            }
        }
    }

    @Override
    public void clearCell(CellRef cellRef) {
        if (cellRef == null || cellRef.getSheetName() == null) return;
        Sheet sheet = workbook.getSheet(cellRef.getSheetName());
        if (sheet == null) return;
        removeCellComment(sheet, cellRef.getRow(), cellRef.getCol());
        Row row = getRowForClearCell(sheet, cellRef);
        if (row == null) return;
        Cell cell = row.getCell(cellRef.getCol());
        if (cell == null) {
            CellAddress cellAddress = new CellAddress(cellRef.getRow(), cellRef.getCol());
            if (sheet.getCellComment(cellAddress) != null) {
                cell = row.createCell(cellRef.getCol());
                cell.removeCellComment();
            }
            return;
        }
        cell.setBlank();
        cell.setCellStyle(workbook.getCellStyleAt(0));
        if (cell.getCellComment() != null) {
            cell.removeCellComment();
        }
        findAndRemoveExistingCellRegion(cellRef);
    }

    protected Row getRowForClearCell(Sheet sheet, CellRef cellRef) {
        return sheet.getRow(cellRef.getRow());
    }

    protected final void removeCellComment(Sheet sheet, int rowNum, int colNum) {
        Row row = sheet.getRow(rowNum);
        if (row == null) return;
        Cell cell = row.getCell(colNum);
        if (cell == null) return;
        cell.removeCellComment();
    }

    @Override
    public List<CellData> getCommentedCells() {
        List<CellData> commentedCells = new ArrayList<>();
        for (SheetData sheetData : sheetMap.values()) {
            for (RowData rowData : sheetData) {
                if (rowData == null) continue;
                int row = ((PoiRowData) rowData).getRow().getRowNum();
                List<CellData> cellDataList = readCommentsFromSheet(((PoiSheetData) sheetData).getSheet(), row);
                commentedCells.addAll(cellDataList);
            }
        }
        return commentedCells;
    }


    

    @Override
    public void write() throws IOException {
        writeButNotCloseStream();
        outputStream.close();
        dispose();
    }

    @Override
    public void writeButNotCloseStream() throws IOException {
        if (outputStream == null) {
            throw new IllegalStateException("Cannot write a workbook with an uninitialized output stream. Was Transformer.setOutputStream() called?");
        }
        if (workbook == null) {
            throw new IllegalStateException("Cannot write an uninitialized workbook");
        }
        if (!isStreaming() && isEvaluateFormulas()) {
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
        }
        if (isFullFormulaRecalculationOnOpening()) {
            workbook.setForceFormulaRecalculation(true);
        }
        workbook.write(outputStream);
    }

    @Override
    public void dispose() {
        // Note that SXSSF allocates temporary files that you must always clean up explicitly, by calling the dispose method. ( http://poi.apache.org/components/spreadsheet/how-to.html#sxssf )
        try {
            if (workbook instanceof SXSSFWorkbook) {
                ((SXSSFWorkbook) workbook).dispose();
            }
        } catch (Exception e) {
            getLogger().warn(e, "Error disposing streamed workbook");
        }
    }

    private List<CellData> readCommentsFromSheet(Sheet sheet, int rowNum) {
        List<CellData> commentDataCells = new ArrayList<>();
        for (Entry<CellAddress, ? extends Comment> e : sheet.getCellComments().entrySet()) {
            if (e.getKey().getRow() == rowNum) {
                Comment comment = e.getValue();
                if (comment.getString() != null) {
                    CellData cellData = new CellData(new CellRef(sheet.getSheetName(), e.getKey().getRow(), e.getKey().getColumn()));
                    cellData.setCellComment(comment.getString().getString());
                    commentDataCells.add(cellData);
                }
            }
        }
        commentDataCells.sort((a, b) -> a.getCol() - b.getCol());
        return commentDataCells;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public CellStyle getCellStyle(CellRef cellRef) {
        SheetData sheetData = sheetMap.get(cellRef.getSheetName());
        PoiCellData cellData = (PoiCellData) sheetData.getRowData(cellRef.getRow()).getCellData(cellRef.getCol());
        return cellData.getCellStyle();
    }

    @Override
    public boolean deleteSheet(String sheetName) {
        if (super.deleteSheet(sheetName)) {
            int sheetIndex = workbook.getSheetIndex(sheetName);
            workbook.removeSheetAt(sheetIndex);
            return true;
        } else {
            getLogger().warn("Failed to find sheet '" + sheetName + "' in sheet map. Skipping the deletion.");
            return false;
        }
    }

    @Override
    public void setHidden(String sheetName, boolean hidden) {
        int sheetIndex = workbook.getSheetIndex(sheetName);
        workbook.setSheetHidden(sheetIndex, hidden);
    }

    @Override
    public void updateRowHeight(String srcSheetName, int srcRowNum, String targetSheetName, int targetRowNum) {
        if (isSXSSF) return;
        SheetData sheetData = sheetMap.get(srcSheetName);
        RowData rowData = sheetData.getRowData(srcRowNum);
        Sheet sheet = workbook.getSheet(targetSheetName);
        if (sheet == null) {
            sheet = workbook.createSheet(targetSheetName);
        }
        Row targetRow = sheet.getRow(targetRowNum);
        if (targetRow == null) {
            targetRow = sheet.createRow(targetRowNum);
        }
        short srcHeight = rowData != null ? (short) rowData.getHeight() : sheet.getDefaultRowHeight();
        targetRow.setHeight(srcHeight);
    }
    
    /**
     * @return xls = null, xlsx = XSSFWorkbook, xlsx with streaming = the inner XSSFWorkbook instance
     */
    public XSSFWorkbook getXSSFWorkbook() {
        if (workbook instanceof SXSSFWorkbook) {
            return ((SXSSFWorkbook) workbook).getXSSFWorkbook();
        }
        if (workbook instanceof XSSFWorkbook) {
            return (XSSFWorkbook) workbook;
        }
        return null;
    }
    
    @Override
    public void adjustTableSize(CellRef ref, Size size) {
        XSSFWorkbook xwb = getXSSFWorkbook();
        if (size.getHeight() > 0 && xwb != null) {
            XSSFSheet sheet = xwb.getSheet(ref.getSheetName());
            if (sheet == null) {
                getLogger().error("Can not access sheet '" + ref.getSheetName() + "'");
            } else {
                for (XSSFTable table : sheet.getTables()) {
                    AreaRef areaRef = new AreaRef(table.getSheetName() + "!" + table.getCTTable().getRef());
                    if (areaRef.contains(ref)) {
                        // Make table higher
                        areaRef.getLastCellRef().setRow(ref.getRow() + size.getHeight() - 1);
                        table.getCTTable().setRef(
                                areaRef.getFirstCellRef().toString(true) + ":" + areaRef.getLastCellRef().toString(true));
                    }
                }
            }
        }
    }
}
