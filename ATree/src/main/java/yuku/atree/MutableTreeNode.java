package yuku.atree;

public interface MutableTreeNode extends TreeNode {
    void insert(MutableTreeNode child, int index);
    void remove(int index);
    void remove(MutableTreeNode node);
    void setUserObject(Object object);
    <T> T getUserObject();
    void removeFromParent();
    void setParent(MutableTreeNode parent);
}