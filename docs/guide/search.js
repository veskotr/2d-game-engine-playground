(() => {
  const filters = document.querySelector(".reference-filters");
  const query = document.querySelector("#feature-query");
  const category = document.querySelector("#feature-category");
  const clear = document.querySelector("#clear-feature-query");
  const status = document.querySelector("#feature-count");
  const empty = document.querySelector("#feature-empty");
  const groups = Array.from(document.querySelectorAll("[data-feature-category]"), group => ({
    group,
    entries: Array.from(group.querySelectorAll("li"), item => ({
      item,
      searchable: `${item.textContent} ${item.dataset.keywords}`.toLowerCase()
    }))
  }));

  function filterFeatures() {
    const terms = query.value.toLowerCase().trim().split(/\s+/).filter(Boolean);
    let count = 0;

    for (const { group, entries } of groups) {
      const included = !category.value || category.value === group.dataset.featureCategory;
      let visible = 0;
      for (const { item, searchable } of entries) {
        item.hidden = !included || !terms.every(term => searchable.includes(term));
        if (!item.hidden) visible++;
      }
      group.hidden = visible === 0;
      count += visible;
    }

    status.textContent = count === 1 ? "1 feature" : `${count} features`;
    empty.hidden = count !== 0;
  }

  query.addEventListener("input", filterFeatures);
  category.addEventListener("change", filterFeatures);
  clear.addEventListener("click", () => {
    query.value = "";
    filterFeatures();
    query.focus();
  });
  window.addEventListener("pageshow", filterFeatures);
  filterFeatures();
  filters.hidden = false;
})();
