package io.gastos.app.ui.guide

import io.gastos.app.ui.home.HomeSwipe

const val GUIDE_TOTAL = 6

/** Bump to show the tour again to everyone who has already seen an earlier version. */
const val GUIDE_VERSION = 2

/** Version 1 had steps 1-5; people who saw it are shown only what version 2 added. */
const val GUIDE_V1 = 1

enum class GuideStep(val number: Int, val text: String, val swipeAxis: HomeSwipe.Axis?) {
    AMOUNT(1, "Enter the amount of your expense", null),
    METHOD(2, "You can change which payment method you used for this expense", HomeSwipe.Axis.METHOD),
    LABEL(3, "Then, you can choose from a set of expense types", HomeSwipe.Axis.LABEL),
    DESCRIPTION(4, "You can also specify what you bought (ex: Jollibee or MTG Cards)", null),
    HISTORY(5, "Finally, keep track of your expenses history here!", null),
    SWIPE_HINT(6, "In your history, swipe an expense left to edit or delete it", null);

    val isLast: Boolean get() = this == SWIPE_HINT
}
