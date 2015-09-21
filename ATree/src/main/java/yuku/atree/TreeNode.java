package yuku.atree;

import android.view.*;


public interface TreeNode {
    <T extends TreeNode> T getChildAt(int childIndex);
    int getChildCount();
    <T extends TreeNode> T getParent();
    int getIndex(TreeNode node);
    boolean isLeaf();
    
    // yuku's additions
    int getDepth();
    int getLevel();
    
    // for converting to list
    int getRowCount();
    boolean getExpanded();
    void setExpanded(boolean expanded);
    View getView(int position, View convertView, ViewGroup parent, int level, TreeNodeIconType iconType, int[] lines);
}
