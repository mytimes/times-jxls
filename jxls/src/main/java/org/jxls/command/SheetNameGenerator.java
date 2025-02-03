package org.jxls.command;

import java.util.List;

import org.jxls.common.CellRef;
import org.jxls.common.Context;
import org.jxls.logging.JxlsLogger;
import org.jxls.transform.SafeSheetNameBuilder;

/**
 * Creates cell references based on passed sheet names
 */
public class SheetNameGenerator implements CellRefGenerator {
    private final List<String> sheetNames;
    private final CellRef startCellRef;

    public SheetNameGenerator(List<String> sheetNames, CellRef startCellRef) {
        this.sheetNames = sheetNames;
        this.startCellRef = startCellRef;
    }

    @Override
    public CellRef generateCellRef(int index, Context context, JxlsLogger logger) {
        String sheetName = index >= 0 && index < sheetNames.size() ? sheetNames.get(index) : null;
        Object builder = RunVar.getRunVar(SafeSheetNameBuilder.CONTEXT_VAR_NAME, context);
        if (builder instanceof SafeSheetNameBuilder) {
            SafeSheetNameBuilder b = (SafeSheetNameBuilder) builder;
            sheetName = b.createSafeSheetName(sheetName, index, logger);
        }
        if (sheetName == null) {
            return null;
        }
        return new CellRef(sheetName, startCellRef.getRow(), startCellRef.getCol());
    }
}
