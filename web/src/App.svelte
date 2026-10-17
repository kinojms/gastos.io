<script lang="ts">
  import { router } from './lib/router.svelte'
</script>

<main class="safe">
  <p>{router.route.name}</p>
</main>
