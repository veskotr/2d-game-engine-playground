embeddedUiLoads = 0
embeddedUiClicks = 0
function EmbeddedUiUnloaded() return embeddedUiLoads == 0 end
function EmbeddedUiLoadedOnce() return embeddedUiLoads == 1 end
function EmbeddedUiClickedOnce() return embeddedUiClicks == 1 end
function EmbeddedUiClick() embeddedUiClicks = embeddedUiClicks + 1 end
