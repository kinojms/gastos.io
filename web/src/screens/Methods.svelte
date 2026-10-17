<script lang="ts">
  import {
    activeMethods, addMethod, archiveMethod, recolorMethod, renameMethod, reorderMethods, validateMethodName,
  } from '../lib/data/methods'
  import { DEFAULT_PAYMENT_METHODS, LABEL_PALETTE, type PaymentMethod } from '../lib/data/models'
  import { confirmCopy } from '../lib/domain/confirmCopy'
  import NamedList, { type NamedListConfig } from '../lib/ui/NamedList.svelte'

  /** Label palette plus the six brand colors, deduped. */
  const SWATCHES = [...new Set([...LABEL_PALETTE, ...DEFAULT_PAYMENT_METHODS.map((m) => m.colorArgb)])]

  const cfg: NamedListConfig<PaymentMethod> = {
    title: 'Payment methods',
    addButton: '+ Add payment method',
    addTitle: 'Add payment method',
    editTitle: 'Edit payment method',
    deleteText: 'Delete payment method',
    palette: SWATCHES,
    load: activeMethods,
    validate: validateMethodName,
    // Delete is hidden when only one active method remains.
    canDelete: (_method, methods) => methods.length > 1,
    copy: {
      add: confirmCopy.addMethod,
      rename: confirmCopy.renameMethod,
      recolor: confirmCopy.recolorMethod,
      reorder: confirmCopy.reorderMethods,
      delete: (method) => confirmCopy.deleteMethod(method.name),
    },
    countFor: async () => 0,
    ops: { add: addMethod, rename: renameMethod, recolor: recolorMethod, reorder: reorderMethods, delete: archiveMethod },
  }
</script>

<NamedList {cfg} />
