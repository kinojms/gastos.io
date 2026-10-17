<script lang="ts">
  import { countByLabel } from '../lib/data/expenses'
  import {
    addLabel, allLabels, deleteLabel, recolorLabel, renameLabel, reorderLabels, validateLabelName,
  } from '../lib/data/labels'
  import { LABEL_PALETTE, type Label } from '../lib/data/models'
  import { confirmCopy } from '../lib/domain/confirmCopy'
  import NamedList, { type NamedListConfig } from '../lib/ui/NamedList.svelte'

  const cfg: NamedListConfig<Label> = {
    title: 'Labels',
    addButton: '+ Add label',
    addTitle: 'Add label',
    editTitle: 'Edit label',
    deleteText: 'Delete label',
    palette: LABEL_PALETTE,
    load: allLabels,
    validate: validateLabelName,
    canDelete: (label) => !label.isOther,
    copy: {
      add: confirmCopy.addLabel,
      rename: confirmCopy.renameLabel,
      recolor: confirmCopy.recolorLabel,
      reorder: confirmCopy.reorderLabels,
      delete: (label, count) => confirmCopy.deleteLabel(label.name, count),
    },
    countFor: (label) => countByLabel(label.id),
    ops: { add: addLabel, rename: renameLabel, recolor: recolorLabel, reorder: reorderLabels, delete: deleteLabel },
  }
</script>

<NamedList {cfg} />
