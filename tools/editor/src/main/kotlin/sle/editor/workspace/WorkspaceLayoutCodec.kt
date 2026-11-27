package sle.editor.workspace

import kotlinx.serialization.json.*

/** Untrusted settings are bounded before JSON parsing and before recursive model construction. */
class WorkspaceLayoutCodec(private val registry: PanelRegistry) {
    companion object { const val MAX_BYTES = 1024 * 1024 }
    private val json = Json { isLenient = false }
    fun encode(layout: DockLayout): String {
        fun bounds(b: LogicalBounds) = buildJsonArray { add(b.x);add(b.y);add(b.width);add(b.height) }
        fun node(n: DockNode): JsonObject = when(n) {
            is DockNode.Tabs -> buildJsonObject {
                put("group",n.group.value);put("panels",buildJsonArray { n.panels.forEach { add(it.value) } });put("active",n.active.value)
            }
            is DockNode.Split -> buildJsonObject { put("axis",n.axis.name);put("ratio",n.ratio);put("first",node(n.first));put("second",node(n.second)) }
        }
        fun placements(p: Map<PanelId,PanelPlacement>) = buildJsonObject {
            p.forEach { (id,v) -> put(id.value,buildJsonObject { put("host",v.host.value);put("group",v.group.value);put("index",v.index);put("bounds",bounds(v.bounds)) }) }
        }
        val text=buildJsonObject {
            put("version",1);put("mainHost",layout.mainHost.value)
            put("hosts",buildJsonArray { layout.hosts.forEach { h -> add(buildJsonObject {
                put("id",h.id.value);put("bounds",bounds(h.bounds));put("root",h.root?.let(::node) ?: JsonNull)
            }) } });put("hidden",placements(layout.hidden));put("minimized",placements(layout.minimized))
        }.toString()
        // Apply the same limits to output. No partial or unreadable layout replaces a valid file.
        decode(text)
        return text
    }
    fun decode(text: String): DockLayout {
        require(text.toByteArray(Charsets.UTF_8).size<=MAX_BYTES) { "Layout exceeds 1 MiB" }
        // Bound parser nesting and reject duplicate JSON keys before map construction can
        // silently discard an earlier panel placement (including escaped key spellings).
        val containers=mutableListOf<MutableSet<String>?>()
        var position=0
        while(position<text.length) {
            when(text[position]) {
                '{','[' -> { containers.add(if(text[position]=='{') mutableSetOf() else null);require(containers.size<=100) { "JSON nesting limit" } }
                '}',']' -> { require(containers.isNotEmpty());containers.removeAt(containers.lastIndex) }
                '"' -> {
                    val start=position++
                    while(position<text.length) {
                        if(text[position]=='\\') position++
                        else if(text[position]=='"') break
                        position++
                    }
                    require(position<text.length)
                    var next=position+1
                    while(next<text.length && text[next].isWhitespace()) next++
                    if(next<text.length && text[next]==':') {
                        val key=json.parseToJsonElement(text.substring(start,position+1)).jsonPrimitive.content
                        require(containers.lastOrNull()?.add(key)==true) { "Duplicate JSON key" }
                    }
                }
            }
            position++
        }
        val data=json.parseToJsonElement(text).jsonObject
        require(data.getValue("version").jsonPrimitive.int==1) { "Unsupported layout version" }
        fun JsonObject.str(k: String): String = getValue(k).jsonPrimitive.content.also { require(it.length in 1..128 && it.isNotBlank()) }
        fun bounds(v: JsonElement): LogicalBounds {
            val a=v.jsonArray;require(a.size==4)
            return LogicalBounds(a[0].jsonPrimitive.float,a[1].jsonPrimitive.float,a[2].jsonPrimitive.float,a[3].jsonPrimitive.float).also { require(it.isValid()) }
        }
        var count=0
        val seen=mutableSetOf<PanelId>();val groups=mutableSetOf<GroupId>()
        fun panel(id: String): PanelId { require(id.length in 1..128);return PanelId(id).also { require(seen.add(it)) { "Duplicate panel" } } }
        fun node(v: JsonElement, depth: Int): DockNode? {
            require(depth<=32 && ++count<=256) { "Layout tree limit" }
            val o=v.jsonObject
            if("group" in o) {
                val group=GroupId(o.str("group"));require(groups.add(group)) { "Duplicate group" }
                val ids=o.getValue("panels").jsonArray.map { panel(it.jsonPrimitive.content) };require(ids.isNotEmpty())
                val active=PanelId(o.str("active"));require(active in ids)
                val known=ids.filter { it in registry.byId }
                return known.takeIf { it.isNotEmpty() }?.let { DockNode.Tabs(group,it,active.takeIf { it in known } ?: known.first()) }
            }
            val ratio=o.getValue("ratio").jsonPrimitive.float;require(ratio.isFinite() && ratio>0 && ratio<1)
            val axis=DockAxis.valueOf(o.str("axis"))
            val a=node(o.getValue("first"),depth+1);val b=node(o.getValue("second"),depth+1)
            return if(a==null) b else if(b==null) a else DockNode.Split(axis,ratio,a,b)
        }
        val main=HostId(data.str("mainHost"))
        val hostsJson=data.getValue("hosts").jsonArray;require(hostsJson.size in 1..33)
        val hosts=hostsJson.map { value -> val o=value.jsonObject
            DockHost(HostId(o.str("id")),o.getValue("root").takeUnless { it==JsonNull }?.let { node(it,1) },bounds(o.getValue("bounds")))
        }
        require(hosts.map { it.id }.distinct().size==hosts.size && hosts.count { it.id==main }==1)
        fun placements(name: String): Map<PanelId,PanelPlacement> = data.getValue(name).jsonObject.entries.mapNotNull { (key,value) ->
            val id=panel(key);val o=value.jsonObject
            val p=PanelPlacement(HostId(o.str("host")),GroupId(o.str("group")),o.getValue("index").jsonPrimitive.int,bounds(o.getValue("bounds")))
            require(p.index in 0..256)
            if(id in registry.byId) id to p else null
        }.toMap()
        val hidden=placements("hidden");val minimized=placements("minimized")
        var layout=DockLayout(main,hosts.filter { it.id==main || it.root!=null || minimized.values.any { p -> p.host==it.id } },hidden,minimized)
        // Newly registered panels join the main host without disturbing saved placements.
        val missing=registry.byId.keys-seen
        if(missing.isNotEmpty()) {
            val home=layout.hosts.single { it.id==main }
            var group=GroupId("recovered");var n=0
            while(group in groups) group=GroupId("recovered-${++n}")
            val visible = missing.filter { id -> registry.byId.getValue(id).let { it.available && !it.temporary } }
            val root=if(visible.isEmpty()) home.root else if(home.root==null) DockNode.Tabs(group,visible,visible.first()) else {
                fun append(node: DockNode): DockNode = when(node) {
                    is DockNode.Tabs -> node.copy(panels=node.panels+visible)
                    is DockNode.Split -> node.copy(first=append(node.first))
                }
                append(home.root)
            }
            val hiddenPanels = (missing - visible.toSet()).associateWith { id ->
                val neighbor = when (id.value) {
                    "tilePalette" -> PanelId("inspector")
                    else -> PanelId("scene")
                }
                val host = layout.hosts.firstOrNull { it.root?.tabs()?.any { tab -> neighbor in tab.panels } == true }
                val anchor = host?.root?.tabs()?.firstOrNull { neighbor in it.panels }
                if (host != null && anchor != null)
                    PanelPlacement(host.id, anchor.group, anchor.panels.size, host.bounds)
                else PanelPlacement(main, group, 0, home.bounds)
            }
            layout=layout.copy(hosts=layout.hosts.map { if(it.id==main) it.copy(root=root) else it },
                hidden=layout.hidden + hiddenPanels)
        }
        require(validateLayout(layout,registry.byId.keys).isEmpty()) { "Malformed layout" }
        return layout
    }
}
