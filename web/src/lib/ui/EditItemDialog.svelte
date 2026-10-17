<script lang="ts">
  import ColorSwatches from './ColorSwatches.svelte'
  import Sheet from './Sheet.svelte'

  let {
    title,
    initialName,
    initialColor,
    palette,
    validate,
    deleteText,
    onsave,
    ondelete,
    onclose,
  }: {
    title: string
    initialName: string
    initialColor: number
    palette: number[]
    validate: (name: string) => string | null
    /** "Delete label": omitted when the item can't be deleted. */
    deleteText?: string
    onsave: (name: string, color: number) => void
    ondelete?: () => void
    onclose: () => void
  } = $props()

  // The form starts from the item as it was when the dialog opened.
  /* svelte-ignore state_referenced_locally */
  let name = $state(initialName)
  /* svelte-ignore state_referenced_locally */
  let color = $state(initialColor)
  let error = $state<string | null>(null)

  function save() {
    const problem = validate(name)
    if (problem) {
      error = problem
      return
    }
    const trimmed = name.trim()
    if (trimmed === initialName && color === initialColor) onclose()
    else onsave(trimmed, color)
  }
</script>

<Sheet open={true} onClose={onclose}>
  <form
    class="form"
    onsubmit={(e) => {
      e.preventDefault()
      save()
    }}
  >
    <h2>{title}</h2>
    <label>
      <span>Name</span>
      <input
        type="text"
        value={name}
        aria-invalid={error !== null}
        oninput={(e) => {
          name = e.currentTarget.value
          error = null
        }}
      />
    </label>
    {#if error}<p class="error" role="alert">{error}</p>{/if}
    <ColorSwatches colors={palette} selected={color} onPick={(c) => (color = c)} />
    {#if deleteText && ondelete}
      <button type="button" class="delete" onclick={ondelete}>{deleteText}</button>
    {/if}
    <div class="buttons">
      <button type="button" class="cancel" onclick={onclose}>Cancel</button>
      <button type="submit" class="save">Save</button>
    </div>
  </form>
</Sheet>

<style>
  .form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  h2 {
    margin: 0;
    font-size: 18px;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  label span {
    font-size: 12px;
    color: var(--muted);
  }

  input {
    width: 100%;
    height: 48px;
    padding: 0 12px;
    border: 1px solid var(--line);
    border-radius: 12px;
    background: var(--bg);
    color: var(--fg);
  }

  input[aria-invalid='true'] {
    border-color: var(--danger);
  }

  .error {
    margin: -6px 0 0;
    color: var(--danger);
    font-size: 13px;
  }

  .delete {
    align-self: flex-start;
    border: 0;
    background: none;
    padding: 6px 0;
    color: var(--danger);
    font-weight: 600;
  }

  .buttons {
    display: flex;
    gap: 8px;
    padding-bottom: 8px;
  }

  .buttons button {
    flex: 1;
    height: 48px;
    border-radius: 12px;
    font-weight: 700;
  }

  .cancel {
    border: 1px solid var(--line);
    background: none;
  }

  .save {
    border: 0;
    background: var(--accent);
    color: var(--on-accent);
  }
</style>
