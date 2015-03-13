package yuku.atree;

public interface TreeListener {
    void onTreeNodesChanged(TreeEvent e);
    void onTreeNodesInserted(TreeEvent e);
    void onTreeNodesRemoved(TreeEvent e);
    void onTreeStructureChanged(TreeEvent e);
}
