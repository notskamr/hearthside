package dev.hearthside.core.e2e;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Which desktop E2E steps a run performs ({@code -Dhearthside.e2e.only=<ids>}): the named steps plus, transitively,
 * the earlier steps they need. With no ids every step runs. A step's needs must come before it in the run order, so
 * one pass from the last step back to the first closes the selection.
 */
public final class StepSelection {
	/** The selected ids, or null when every step runs. */
	private final Set<String> selected;

	private StepSelection(Set<String> selected) {
		this.selected = selected;
	}

	/**
	 * @param only comma-separated step ids; blank or null selects every step
	 * @param orderedIds every step id, in run order
	 * @param needs for a step id, the earlier step ids whose effects it relies on
	 * @throws IllegalArgumentException for an unknown id in {@code only}, or a need that doesn't come before its step
	 */
	public static StepSelection parse(String only, List<String> orderedIds, Map<String, List<String>> needs) {
		Map<String, Integer> position = new HashMap<>();
		for (String id : orderedIds) {
			if (position.putIfAbsent(id, position.size()) != null) {
				throw new IllegalArgumentException("E2E step " + id + " is listed twice");
			}
		}
		needs.forEach((id, required) -> {
			Integer at = position.get(id);
			if (at == null) {
				throw new IllegalArgumentException("E2E needs name " + id + ", which is not a step; known steps: "
						+ String.join(", ", orderedIds));
			}
			for (String need : required) {
				Integer needAt = position.get(need);
				if (needAt == null || needAt >= at) {
					throw new IllegalArgumentException("E2E step " + id + " needs " + need + ", which doesn't come before it");
				}
			}
		});
		if (only == null || only.isBlank()) {
			return new StepSelection(null);
		}
		Set<String> selected = new HashSet<>();
		for (String raw : only.split(",")) {
			String id = raw.strip();
			if (id.isEmpty()) {
				continue;
			}
			if (!position.containsKey(id)) {
				throw new IllegalArgumentException("Unknown E2E step " + id + "; known steps: " + String.join(", ", orderedIds));
			}
			selected.add(id);
		}
		for (int i = orderedIds.size() - 1; i >= 0; i--) {
			String id = orderedIds.get(i);
			if (selected.contains(id)) {
				selected.addAll(needs.getOrDefault(id, List.of()));
			}
		}
		return new StepSelection(Set.copyOf(selected));
	}

	/** Whether the step runs. With every step selected, so does an id this selection was never told about. */
	public boolean runs(String id) {
		return selected == null || selected.contains(Objects.requireNonNull(id));
	}

	/** Whether any of these steps runs (a section with none is skipped as a whole). */
	public boolean anyRuns(Collection<String> ids) {
		return ids.stream().anyMatch(this::runs);
	}

	/** Whether every step runs (no ids were named). */
	public boolean everything() {
		return selected == null;
	}
}
