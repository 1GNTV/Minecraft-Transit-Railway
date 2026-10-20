package org.mtr.mod.builder;

import java.util.*;

/** Ordered editing with stable grouped moves; no mutation reaches MTR before commit. */
public final class ConsistDraft<T> {
    private final List<T> items;
    private final BitSet selected = new BitSet();
    private final Deque<List<T>> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private int anchor = -1;
    public ConsistDraft(List<T> items) { this.items = items; }
    public BitSet selection() { return (BitSet) selected.clone(); }
    public boolean selected(int index) { return selected.get(index); }
    public int selectedCount() { return selected.cardinality(); }
    public void select(int index, boolean range, boolean toggle) {
        if (index < 0 || index >= items.size()) return;
        if (range && anchor >= 0) selected.set(Math.min(anchor, index), Math.max(anchor, index) + 1);
        else if (toggle) { selected.flip(index); anchor = index; }
        else { selected.clear(); selected.set(index); anchor = index; }
    }
    public void clearSelection() { selected.clear(); anchor = -1; }
    private void checkpoint() {
        undo.push(List.copyOf(items)); while (undo.size() > 40) undo.removeLast(); redo.clear();
    }
    public void add(T item) { checkpoint(); items.add(item); select(items.size() - 1, false, false); }
    public void replace(List<T> values) { checkpoint(); items.clear(); items.addAll(values); clearSelection(); }
    public void reverse() { checkpoint(); Collections.reverse(items); clearSelection(); }
    public List<T> selectedItems() { List<T> out = new ArrayList<>(); for (int i = selected.nextSetBit(0); i >= 0 && i < items.size(); i = selected.nextSetBit(i + 1)) out.add(items.get(i)); return out; }
    public void duplicate(int maximum) {duplicate(maximum,java.util.function.UnaryOperator.identity());}
    public void duplicate(int maximum,java.util.function.UnaryOperator<T> copy) {
        if (selected.isEmpty() || items.size() + selected.cardinality() > maximum) return;
        checkpoint(); var copies = selectedItems().stream().map(copy).toList(); int at = selected.length();
        items.addAll(at, copies); selected.clear(); selected.set(at, at + copies.size()); anchor = at;
    }
    public List<T> delete() {
        if (selected.isEmpty()) return List.of();
        checkpoint(); List<T> removed = selectedItems();
        for (int i = selected.length() - 1; i >= 0; i--) if (selected.get(i)) items.remove(i);
        clearSelection(); return removed;
    }
    public void move(int insertion) {
        if (selected.isEmpty()) return;
        insertion = Math.max(0, Math.min(items.size(), insertion));
        int before = selected.get(0, insertion).cardinality(), count = selected.cardinality();
        List<T> moving = selectedItems(), result = new ArrayList<>(items);
        for (int i = selected.length() - 1; i >= 0; i--) if (selected.get(i)) result.remove(i);
        int at = insertion - before; result.addAll(at, moving);
        if (result.equals(items)) return;
        checkpoint(); items.clear(); items.addAll(result);
        selected.clear(); selected.set(at, at + count); anchor = at;
    }
    public void undo() { if (undo.isEmpty()) return; redo.push(List.copyOf(items)); restore(undo.pop()); }
    public void redo() { if (redo.isEmpty()) return; undo.push(List.copyOf(items)); restore(redo.pop()); }
    private void restore(List<T> values) { items.clear(); items.addAll(values); clearSelection(); }
}
