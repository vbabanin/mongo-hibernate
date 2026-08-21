/*
 * Copyright 2025-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.hibernate.internal.translate.mongoast;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Assigns a canonical value number (VN) to each distinct structural expression key. Two calls to {@link #intern(String,
 * Object...)} with equal tags and equal field lists return the same integer.
 *
 * <p>Used to give {@link AstExpression} instances a structure-based identity in O(1) after child VNs are known,
 * enabling GROUP BY key matching without repeated subtree comparisons.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
/*
Name in compiler theory: Value Numbering — assigning a canonical integer ("value number") to each distinct expression structure, so two structurally-equal expressions share one number. Ours is specifically Structural Value Numbering (SVN), implemented via hash-consing — the interning technique that gives immutable values a
*   canonical identity.
*
*   Roots:
*   - Value numbering: Cocke 1970 (local, straight-line code); Alpern-Wegman-Zadeck 1988 (global VN across control-flow); Rosen-Wegman-Zadeck 1988 & Simpson 1996 (dominator-based GVN); LLVM's NewGVN today.
*   - Hash-consing: Ershov 1958, Goto 1974 ("Monocopy and Associative Algorithms in Extended Lisp"), Filliâtre-Conchon 2006 ("Type-Safe Modular Hash-Consing") — the modern typed formulation.
*
*   Semantic vs structural: some GVN variants use algebraic reasoning (x+1 == 1+x, x*2 == x<<1). Ours is structural — only same-shape trees are equal. That's what PostgreSQL's GROUP BY membership check uses too, so behavior aligns.
*/
public final class VNRegistry {

    private record Key(String tag, List<Object> fields) {}

    private final Map<Key, Integer> table = new HashMap<>();
    private final IdentityHashMap<AstExpression, Integer> nodeCache = new IdentityHashMap<>();
    private int next;

    public int intern(String tag, Object... fields) {
        return table.computeIfAbsent(new Key(tag, List.of(fields)), k -> next++);
    }

    /**
     * Returns the value number for {@code node}, computing it via {@code compute} on first call and caching it against
     * the node's identity so subsequent calls on the same instance return in O(1) without re-walking children.
     */
    public int memoize(AstExpression node, ToIntFunction<VNRegistry> compute) {
        Integer cached = nodeCache.get(node);
        if (cached != null) {
            return cached;
        }
        int result = compute.applyAsInt(this);
        nodeCache.put(node, result);
        return result;
    }
}
