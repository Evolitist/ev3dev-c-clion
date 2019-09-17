package com.evolitist.ev3c.toolwindow

import com.evolitist.ev3c.action
import com.evolitist.ev3c.component.Ev3devConnector
import com.evolitist.ev3c.iconAction
import com.evolitist.ev3c.wrap
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ssh.RemoteFileObject
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import javax.swing.JComponent
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class Ev3devFilesToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(p0: Project, p1: ToolWindow) {
        val factory = ContentFactory.SERVICE.getInstance()
        val component = Ev3devConnector.getInstance(p0)
        (p1 as ToolWindowEx).setAdditionalGearActions(DefaultActionGroup(
                action("Restart UI") {
                    // TODO: debug, debug, DEBUG
                    val connector = Ev3devConnector.getInstance(it.project!!)
                    connector.sftp ?: return@action
                    connector("echo 'maker' | sudo -S systemctl restart brickman")
                }
        ))
        p1.contentManager.addContent(factory.createContent(
                fileTree(component, "/home/robot"), "Home", false
        ))
        p1.contentManager.addContent(factory.createContent(
                fileTree(component, "/"), "Root", false
        ))
    }

    override fun isDoNotActivateOnStart() = true

    private fun fileTree(conn: Ev3devConnector, path: String): JComponent {
        val panel = SimpleToolWindowPanel(true)
        val tree = SftpFileTree(conn, path)
        val pane = JBScrollPane(tree)
        panel.setContent(pane)
        val toolbar = DefaultActionGroup(
                iconAction(AllIcons.Actions.Refresh, "Refresh") {
                    conn.fireSftpUpdateEvent()
                },
                Separator(),
                iconAction(AllIcons.Actions.GC, "Delete") {
                    if (tree.selectionCount == 0) return@iconAction
                    val model = tree.model as DefaultTreeModel
                    tree.selectionPaths
                            .map { it.lastPathComponent as DefaultMutableTreeNode }
                            .forEach { node ->
                                val file = node.userObject as SftpFile
                                if (file.rm()) {
                                    model.removeNodeFromParent(node)
                                }
                            }
                }
        )
        panel.toolbar = ActionManager.getInstance().createActionToolbar("TB", toolbar, true).component
        return panel
    }

    class SftpFileTree(conn: Ev3devConnector, path: String) : Tree(), TreeWillExpandListener {
        init {
            conn.addSftpListener {
                model = DefaultTreeModel(createNode(it?.file(path)?.wrap()), true)
            }
            addTreeWillExpandListener(this)
            isEditable = true
            setShowsRootHandles(true)
            setRowHeight(20)
            setCellRenderer(DefaultTreeCellRenderer().apply {
                leafIcon = AllIcons.FileTypes.Any_type
                openIcon = AllIcons.Actions.Menu_open
                closedIcon = AllIcons.Nodes.Folder
            })
        }

        override fun treeWillCollapse(event: TreeExpansionEvent) {}

        override fun treeWillExpand(event: TreeExpansionEvent) {
            val node = event.path.lastPathComponent as DefaultMutableTreeNode
            if (node.childCount > 0) {
                return
            }
            val dir = node.userObject as SftpFile
            val children = dir.list()
            if (children.isNotEmpty()) {
                children.map { it.wrap() }
                        .filter { !it.toString().startsWith(".") }
                        .sorted()
                        .forEach {
                            node.add(DefaultMutableTreeNode(it, it.isDir()))
                        }
            }
        }

        companion object {
            private fun createNode(file: SftpFile?): DefaultMutableTreeNode? = if (file != null) {
                val node = DefaultMutableTreeNode(file, file.isDir())
                if (file.isDir()) {
                    file.list()
                            .map { it.wrap() }
                            .filter { !it.toString().startsWith(".") }
                            .sorted()
                            .forEach {
                                node.add(DefaultMutableTreeNode(it, it.isDir()))
                            }
                }
                node
            } else {
                null
            }
        }
    }

    class SftpFile(private val file: RemoteFileObject) : Comparable<SftpFile> {
        //fun exists() = file.exists()
        fun isDir() = file.isDir()
        fun list() = file.list()
        fun rm() = file.rm()

        override fun toString(): String {
            val name = file.toString().substringAfterLast("/").substringBeforeLast("'")
            return if (file.isDir()) "$name/" else name
        }

        override fun compareTo(other: SftpFile): Int {
            if (isDir() && !other.isDir()) return -1
            if (!isDir() && other.isDir()) return 1
            return toString().compareTo(other.toString(), true)
        }
    }
}
