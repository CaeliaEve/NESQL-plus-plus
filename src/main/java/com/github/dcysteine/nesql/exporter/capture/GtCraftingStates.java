package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;

/** Finite exported-candidate states of the PINNED GT crafting result pipeline.
 * Input traits must come from audited native callbacks on owned stacks. This core
 * neither calls unknown APIs nor interprets Java/GUI code. Witnesses are examples,
 * not an assertion that each input combination has been explicitly enumerated.
 */
final class GtCraftingStates {
    private static final int STATE_LIMIT = 65536;
    private static final long TRANSITION_LIMIT = 8388608;

    static final class Candidate {
        final ItemStack stack;
        final int transfer, matches, metadata;
        final String tag;
        Candidate(ItemStack stack, int transfer, int matches) {
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0 || matches < 0 || matches >= 512)
                throw new Jobs.Fault("recipe_unsupported", "Invalid prepared native GT crafting candidate");
            this.stack = stack.copy(); this.transfer = transfer; this.matches = matches;
            metadata = NativeItemMetadata.value(stack);
            tag = stack.hasTagCompound() ? CanonicalJson.digest(TypedNbt.encode(stack.getTagCompound())) : null;
        }
    }

    static final class Rules {
        final boolean keeping, overwrite, shapeless;
        final Item resultItem; final int resultMeta;
        Rules(boolean keeping, boolean overwrite, boolean shapeless, ItemStack normalizedBase) {
            if (normalizedBase == null || normalizedBase.getItem() == null || normalizedBase.stackSize <= 0
                    || overwrite && !shapeless) throw new Jobs.Fault("recipe_unsupported", "Invalid native GT state rules");
            this.keeping = keeping; this.overwrite = overwrite; this.shapeless = shapeless;
            resultItem = normalizedBase.getItem(); resultMeta = NativeItemMetadata.value(normalizedBase);
        }
    }

    static final class State {
        final int transfer, remaining;
        final int[] witness, order;
        final boolean seen;
        final Candidate inherited, overwritten;
        State(int transfer, int remaining, int[] witness, int[] order, boolean seen, Candidate inherited, Candidate overwritten) {
            this.transfer = transfer; this.remaining = remaining; this.witness = witness;
            this.order = order;
            this.seen = seen; this.inherited = inherited; this.overwritten = overwritten;
        }
        private String key() {
            int used = 0; for (int slot = 0; slot < witness.length; slot++) if (witness[slot] >= 0) used |= 1 << slot;
            return used + ":" + transfer + ":" + remaining + ":" + seen + ":" + (inherited == null ? "-" : inherited.tag)
                    + ":" + (overwritten == null ? "-" : overwritten.tag);
        }
    }

    /** Groups use logical ingredient order; offeredOrder is the actual row-major
     * placement (with grid gaps omitted). Shapeless allocation follows native first
     * remaining matching requirement, including overlapping ingredient predicates.
     */
    static List<State> prepare(Rules rules, List<List<Candidate>> groups, int[] offeredOrder) {
        int count = groups.size();
        if (count < 1 || count > 9 || offeredOrder != null && offeredOrder.length != count || offeredOrder == null && !rules.shapeless)
            throw new Jobs.Fault("recipe_unsupported", "Invalid native GT offered inventory order");
        boolean[] seen = new boolean[count];
        if (offeredOrder != null) for (int slot : offeredOrder) {
            if (slot < 0 || slot >= count || seen[slot]) throw new Jobs.Fault("recipe_unsupported", "Native GT offered order is not a permutation");
            seen[slot] = true;
        }
        for (List<Candidate> group : groups) if (group == null || group.isEmpty() || group.size() > STATE_LIMIT)
            throw new Jobs.Fault("recipe_unsupported", "Invalid native GT candidate group");
        int[] unset = new int[count]; Arrays.fill(unset, -1);
        List<State> states = Collections.singletonList(new State(0, rules.shapeless ? (1 << count) - 1 : 0, unset, unset.clone(), false, null, null));
        long transitions = 0;
        for (int depth = 0; depth < count; depth++) {
            LinkedHashMap<String, State> next = new LinkedHashMap<>();
            for (State state : states) for (int slot = 0; slot < count; slot++) {
                if (state.witness[slot] >= 0 || offeredOrder != null && offeredOrder[depth] != slot) continue;
                List<Candidate> candidates = groups.get(slot);
                for (int index = 0; index < candidates.size(); index++) {
                Jobs.checkpoint();
                if (++transitions > TRANSITION_LIMIT) throw new Jobs.Fault("recipe_limit", "Native GT state transitions exceed their budget");
                Candidate candidate = candidates.get(index);
                if (rules.keeping && state.seen && !sameTags(state.inherited, candidate)) continue;
                int remaining = state.remaining;
                if (rules.shapeless) {
                    int available = remaining & candidate.matches;
                    if (available == 0) continue;
                    remaining &= ~Integer.lowestOneBit(available);
                }
                Candidate inherited = rules.keeping && !state.seen ? candidate : state.inherited;
                Candidate overwritten = state.overwritten;
                // Keeping tags makes all root keys equal; current result tags win the
                // overwrite merge, so a second carrier cannot change the result.
                if (!rules.keeping && rules.overwrite && overwritten == null && candidate.stack.hasTagCompound()
                        && candidate.stack.getItem() == rules.resultItem
                        && (candidate.metadata == rules.resultMeta || candidate.metadata == 32767 || rules.resultMeta == 32767))
                    overwritten = candidate;
                int[] witness = state.witness.clone(); witness[slot] = index;
                int[] order = state.order.clone(); order[depth] = slot;
                // Java int overflow is the installed native rule; never widen or clamp.
                State advanced = new State(state.transfer + candidate.transfer, remaining, witness, order, true, inherited, overwritten);
                next.putIfAbsent(advanced.key(), advanced);
                if (next.size() > STATE_LIMIT) throw new Jobs.Fault("recipe_limit", "Native GT result state space exceeds its budget");
                }
            }
            states = new ArrayList<>(next.values());
        }
        return states;
    }

    /** Subset DP retains only rule-relevant states across all offered permutations.
     * The same explicit state/transition budgets apply; no n! tuple expansion.
     */
    static List<State> prepareAnyOrder(Rules rules, List<List<Candidate>> groups) {
        return prepare(rules, groups, null);
    }

    private static boolean sameTags(Candidate first, Candidate next) {
        boolean a = first.stack.hasTagCompound(), b = next.stack.hasTagCompound();
        if (a != b) return false;
        // Exact native equality: signed float zeros equal, NaNs unequal, empty-list
        // element types retained. Hashes only address result tags, never match them.
        NBTTagCompound tags = first.stack.getTagCompound();
        return !a || tags.equals(next.stack.getTagCompound());
    }
}
