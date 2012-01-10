package com.jayantkrish.jklol.cfg;

import java.util.List;
import java.util.ArrayList;

/**
 * A CFG parse tree.
 */
public class ParseTree implements Comparable<ParseTree> {

  private Object root;
  private List<Object> terminal;
  
  private ParseTree left;
  private ParseTree right;

  private double prob;

  /**
   * Create a new parse tree by composing two subtrees with production rule bp.
   */
  public ParseTree(Object root, ParseTree left, ParseTree right, double prob) {
    this.root = root;
    this.left = left;
    this.right = right;
    this.terminal = null;
    this.prob = prob;
  }

  /**
   * Create a new terminal parse tree with a terminal production rule.
   */
  public ParseTree(Object root, List<Object> terminal, double prob) {
    this.root = root;
    this.terminal = terminal;
    this.prob = prob;
  }

  public double getProbability() {
    return prob;
  }

  public int compareTo(ParseTree other) {
    return Double.compare(this.prob, other.prob);
  }

  /**
   * Returns true if this tree has no subtrees associated with it (i.e., it's a
   * leaf).
   */
  public boolean isTerminal() {
    return terminal != null;
  }

  /**
   * Get the node at the root of the parse tree.
   */
  public Object getRoot() {
    return root;
  }

  /**
   * Get the left subtree. Requires the tree to be non-terminal.
   */
  public ParseTree getLeft() {
    assert !isTerminal();
    return left;
  }

  /**
   * Get the right subtree. Requires the tree to be non-terminal.
   */
  public ParseTree getRight() {
    assert !isTerminal();
    return right;
  }

  /**
   * Gets a new parse tree equivalent to this one, with probability
   * {@code this.probability * amount}.
   * 
   * @param amount
   * @return
   */
  public ParseTree multiplyProbability(double amount) {
    if (isTerminal()) {
      return new ParseTree(root, terminal, getProbability() * amount);
    } else {
      return new ParseTree(root, left, right, getProbability() * amount);
    }
  }

  public List<Object> getTerminalProductions() {
    List<Object> prods = new ArrayList<Object>();
    getTerminalProductions(prods);
    return prods;
  }

  public void getTerminalProductions(List<Object> toAppend) {
    if (isTerminal()) {
      toAppend.addAll(terminal);
    } else {
      left.getTerminalProductions(toAppend);
      right.getTerminalProductions(toAppend);
    }
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((left == null) ? 0 : left.hashCode());
    result = prime * result + ((right == null) ? 0 : right.hashCode());
    result = prime * result + ((root == null) ? 0 : root.hashCode());
    result = prime * result + ((terminal == null) ? 0 : terminal.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ParseTree other = (ParseTree) obj;
    if (left == null) {
      if (other.left != null)
        return false;
    } else if (!left.equals(other.left))
      return false;
    if (right == null) {
      if (other.right != null)
        return false;
    } else if (!right.equals(other.right))
      return false;
    if (root == null) {
      if (other.root != null)
        return false;
    } else if (!root.equals(other.root))
      return false;
    if (terminal == null) {
      if (other.terminal != null)
        return false;
    } else if (!terminal.equals(other.terminal))
      return false;
    return true;
  }

  @Override
  public String toString() {
    if (!isTerminal()) {
      return "(" + root + " --> " + left.toString() + " " + right.toString() + ")";
    }
    return "(" + root + "-->" + terminal + ")";
  }
}
