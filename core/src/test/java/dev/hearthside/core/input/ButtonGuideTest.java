package dev.hearthside.core.input;

import static dev.hearthside.core.input.ControlScheme.BEDROCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class ButtonGuideTest {
	static GuideContext world(boolean block, boolean entity, boolean usable) {
		return new GuideContext(false, false, block, entity, usable, false, false);
	}

	static GuideContext container(boolean holding) {
		return new GuideContext(true, false, false, false, false, holding, false);
	}

	static List<String> labels(List<ButtonGuide.Prompt> prompts) {
		return prompts.stream().map(p -> p.control() + " " + p.labelKey()).toList();
	}

	@Test
	void lookingAtBlockShowsMineAndPlace() {
		assertThat(labels(ButtonGuide.prompts(world(true, false, true), BEDROCK))).containsExactly(
				"RIGHT_TRIGGER hearthside.guide.mine", "LEFT_TRIGGER hearthside.guide.place",
				"A hearthside.guide.jump", "B hearthside.guide.sneak", "Y hearthside.guide.inventory");
	}

	@Test
	void lookingAtEntityShowsAttack() {
		assertThat(labels(ButtonGuide.prompts(world(false, true, false), BEDROCK)))
				.startsWith("RIGHT_TRIGGER hearthside.guide.attack");
	}

	@Test
	void holdingSomethingUsableInTheAirShowsUse() {
		assertThat(labels(ButtonGuide.prompts(world(false, false, true), BEDROCK)))
				.startsWith("LEFT_TRIGGER hearthside.guide.use");
	}

	@Test
	void flyingShowsFlyUpAndDown() {
		var flying = new GuideContext(false, false, false, false, false, false, true);
		assertThat(labels(ButtonGuide.prompts(flying, BEDROCK)))
				.contains("A hearthside.guide.fly_up", "B hearthside.guide.fly_down");
	}

	@Test
	void containerShowsTakeHalfAndQuickMove() {
		assertThat(labels(ButtonGuide.prompts(container(false), BEDROCK))).containsExactly(
				"A hearthside.guide.take", "X hearthside.guide.take_half", "Y hearthside.guide.quick_move",
				"B hearthside.guide.close");
	}

	@Test
	void holdingAnItemSwitchesToPlace() {
		assertThat(labels(ButtonGuide.prompts(container(true), BEDROCK))).containsExactly(
				"A hearthside.guide.place", "X hearthside.guide.place_one", "B hearthside.guide.close");
	}

	@Test
	void otherScreensShowSelectAndBack() {
		var menu = new GuideContext(false, true, false, false, false, false, false);
		assertThat(labels(ButtonGuide.prompts(menu, BEDROCK)))
				.containsExactly("A hearthside.guide.select", "B hearthside.guide.back");
	}

	@Test
	void unboundActionsAreOmitted() {
		var gameplay = new HashMap<>(BEDROCK.gameplay());
		gameplay.remove(Action.INVENTORY);
		var scheme = new ControlScheme(gameplay, BEDROCK.menu(), 1f, false, 0.15f, false, true, true);
		assertThat(labels(ButtonGuide.prompts(world(false, false, false), scheme)))
				.noneMatch(l -> l.contains("inventory"));
	}

	@Test
	void remappedControlIsShown() {
		var scheme = BEDROCK.withBinding(Action.JUMP, Control.Y);
		assertThat(labels(ButtonGuide.prompts(world(false, false, false), scheme))).contains("Y hearthside.guide.jump");
	}

	@Test
	void hiddenWhenGuideDisabled() {
		var off = new ControlScheme(BEDROCK.gameplay(), BEDROCK.menu(), 1f, false, 0.15f, false, true, false);
		assertThat(ButtonGuide.prompts(world(true, true, true), off)).isEmpty();
	}

	@Test
	void neverMoreThanSix() {
		assertThat(ButtonGuide.prompts(world(true, true, true), BEDROCK)).hasSizeLessThanOrEqualTo(6);
	}
}
