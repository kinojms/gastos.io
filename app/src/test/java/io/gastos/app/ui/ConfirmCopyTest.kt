package io.gastos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmCopyTest {
    @Test fun expenseCopy() {
        assertEquals("Save changes to ₱45.50 · Transpo?", ConfirmCopy.saveExpense(4550, "Transpo"))
        assertEquals("Delete ₱45.50 · Transpo?", ConfirmCopy.deleteExpense(4550, "Transpo"))
    }

    @Test fun labelCopy() {
        assertEquals("Add label Pets?", ConfirmCopy.addLabel("Pets"))
        assertEquals("Rename Food to Meals?", ConfirmCopy.renameLabel("Food", "Meals"))
        assertEquals("Change color of Food?", ConfirmCopy.recolorLabel("Food"))
        assertEquals("Save new label order?", ConfirmCopy.reorderLabels())
    }

    @Test fun deleteLabelCounts() {
        assertEquals("Delete label Pets?", ConfirmCopy.deleteLabel("Pets", 0))
        assertEquals("Delete label Pets? 1 expense moves to Other.", ConfirmCopy.deleteLabel("Pets", 1))
        assertEquals("Delete label Pets? 12 expenses move to Other.", ConfirmCopy.deleteLabel("Pets", 12))
    }

    @Test fun backupCopy() {
        assertEquals("Restore from GitHub? This replaces all data on this phone.", ConfirmCopy.restore())
        assertEquals("Start fresh? Any existing backup in the repo will be replaced with this phone's data.", ConfirmCopy.startFresh())
        assertEquals("Save backup settings?", ConfirmCopy.saveBackupSettings())
    }
}
