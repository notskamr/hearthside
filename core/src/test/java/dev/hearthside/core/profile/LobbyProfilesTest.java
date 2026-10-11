package dev.hearthside.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.profile.ProfileCarousel.Entry;
import dev.hearthside.core.profile.QuickSetup.Step;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LobbyProfilesTest {
	@TempDir
	Path dir;

	static GuestProfile profile(String name, String guid) {
		return new GuestProfile(UUID.nameUUIDFromBytes(name.getBytes()), name, SkinRef.defaultSkin("steve"), guid);
	}

	final GuestProfile sam = profile("Sam", "pad-model-1");
	final GuestProfile jo = profile("Joe", "pad-model-2");
	final GuestProfile kit = profile("Kit", "pad-model-2");

	@Test
	void carouselPreselectsTheControllersLastProfile() {
		// Stored least recently used first: the last profile used on this controller model is Kit.
		var carousel = new ProfileCarousel(List.of(sam, jo, kit), Set.<UUID>of()::contains, "pad-model-2");
		assertThat(carousel.current()).isEqualTo(new Entry.Profile(kit));
	}

	@Test
	void carouselSkipsProfilesHeldByOtherSeats() {
		// Two identical controllers share a model GUID: the second seat gets the other profile that used it.
		var carousel = new ProfileCarousel(List.of(sam, jo, kit), Set.of(kit.id())::contains, "pad-model-2");
		assertThat(carousel.current()).isEqualTo(new Entry.Profile(jo));
		assertThat(carousel.entries()).doesNotContain(new Entry.Profile(kit));
	}

	@Test
	void carouselTakesAnUpdatedProfileInPlace() {
		// Saved from the controls screen while the card is open: picking it must not bring back the old controls.
		var carousel = new ProfileCarousel(List.of(sam, jo, kit), Set.<UUID>of()::contains, "pad-model-1");
		GuestProfile updated = jo.withControls(ControlScheme.SOUTHPAW);
		carousel.replace(updated);
		assertThat(carousel.entries()).containsExactly(new Entry.Profile(sam), new Entry.Profile(updated),
				new Entry.Profile(kit), new Entry.New());
		assertThat(carousel.current()).isEqualTo(new Entry.Profile(sam));
		carousel.replace(profile("Nobody", null));
		assertThat(carousel.entries()).hasSize(4);
	}

	@Test
	void carouselDropsADeletedProfile() {
		// Deleted from Manage profiles while a card shows it: picking it must not bring it back.
		var carousel = new ProfileCarousel(List.of(sam, jo, kit), Set.<UUID>of()::contains, "pad-model-2");
		assertThat(carousel.current()).isEqualTo(new Entry.Profile(kit));
		carousel.remove(kit.id());
		assertThat(carousel.entries()).containsExactly(new Entry.Profile(sam), new Entry.Profile(jo), new Entry.New());
		assertThat(carousel.current()).isEqualTo(new Entry.New());
		carousel.remove(sam.id());
		carousel.next();
		assertThat(carousel.current()).isEqualTo(new Entry.Profile(jo));
	}

	@Test
	void carouselWithNoMatchStartsAtTheFirstProfile() {
		assertThat(new ProfileCarousel(List.of(sam, jo), Set.<UUID>of()::contains, "other").current()).isEqualTo(new Entry.Profile(sam));
		assertThat(new ProfileCarousel(List.of(sam, jo), Set.<UUID>of()::contains, null).current()).isEqualTo(new Entry.Profile(sam));
	}

	@Test
	void carouselSkipsProfilesTakenAfterItOpened() {
		// Another seat confirms Sam while this card is open: this card stops offering Sam at once.
		java.util.Set<UUID> held = new java.util.HashSet<>();
		var carousel = new ProfileCarousel(List.of(sam, jo), held::contains, null);
		assertThat(carousel.current()).isEqualTo(new Entry.Profile(sam));
		held.add(sam.id());
		assertThat(carousel.current()).isEqualTo(new Entry.Profile(jo));
		assertThat(carousel.entries()).containsExactly(new Entry.Profile(jo), new Entry.New());
		carousel.next();
		carousel.next();
		assertThat(carousel.current()).isEqualTo(new Entry.Profile(jo));
	}

	@Test
	void carouselEndsWithNewAndWraps() {
		var carousel = new ProfileCarousel(List.of(sam, jo), Set.<UUID>of()::contains, null);
		assertThat(carousel.entries()).last().isEqualTo(new Entry.New());
		carousel.previous();
		assertThat(carousel.current()).isEqualTo(new Entry.New());
		carousel.next();
		assertThat(carousel.current()).isEqualTo(new Entry.Profile(sam));
		carousel.next();
		carousel.next();
		assertThat(carousel.current()).isEqualTo(new Entry.New());
	}

	@Test
	void carouselWithNoProfilesOffersOnlyNew() {
		var carousel = new ProfileCarousel(List.of(sam), Set.of(sam.id())::contains, "pad-model-1");
		assertThat(carousel.entries()).containsExactly(new Entry.New());
		assertThat(carousel.current()).isEqualTo(new Entry.New());
	}

	@Test
	void suggestedNameAvoidsTakenNames() {
		var setup = new QuickSetup(List.of("Ember", "hazel"), "CINDER", List.of(), 1);
		assertThat(setup.suggestedName()).isEqualTo("Moss");
		assertThat(setup.nameAvailable("Moss")).isTrue();
		assertThat(setup.nameAvailable("ember")).isFalse();
		assertThat(setup.nameAvailable("cinder")).isFalse();
		assertThat(setup.nameAvailable("ab")).isFalse();
	}

	@Test
	void suggestedNameFallsBackToPlayerN() {
		var taken = new java.util.ArrayList<>(QuickSetup.SUGGESTIONS);
		assertThat(new QuickSetup(taken, "Host", List.of(), 2).suggestedName()).isEqualTo("Player3");
		taken.add("Player3");
		assertThat(new QuickSetup(taken, "Host", List.of(), 2).suggestedName()).isEqualTo("Player3_2");
	}

	@Test
	void quickSetupStepsForwardAndBack() {
		var setup = new QuickSetup(List.of(), "Host", List.of(), 1);
		assertThat(setup.step()).isEqualTo(Step.NAME);
		assertThat(setup.back()).isFalse();
		setup.next();
		assertThat(setup.step()).isEqualTo(Step.SKIN);
		setup.next();
		assertThat(setup.step()).isEqualTo(Step.COLOR);
		assertThat(setup.back()).isTrue();
		assertThat(setup.step()).isEqualTo(Step.SKIN);
		setup.next();
		setup.next();
		assertThat(setup.step()).isEqualTo(Step.DONE);
		setup.next();
		assertThat(setup.step()).isEqualTo(Step.DONE);
	}

	@Test
	void quickSetupOffersEighteenBuiltInSkins() {
		assertThat(QuickSetup.SKINS).hasSize(18);
		assertThat(QuickSetup.SKINS.get(0)).isEqualTo(new SkinRef(SkinRef.Kind.DEFAULT, "steve", false));
		assertThat(QuickSetup.SKINS.get(9)).isEqualTo(new SkinRef(SkinRef.Kind.DEFAULT, "steve", true));
		var setup = new QuickSetup(List.of(), "Host", List.of(), 3);
		assertThat(setup.skin()).isEqualTo(QuickSetup.SKINS.get(3));
		setup.previousSkin();
		setup.previousSkin();
		setup.previousSkin();
		setup.previousSkin();
		assertThat(setup.skin()).isEqualTo(QuickSetup.SKINS.get(17));
	}

	@Test
	void quickSetupPicksTheFirstFreeColour() {
		var setup = new QuickSetup(List.of(), "Host", List.of(PlayerColors.DEFAULTS[0], PlayerColors.DEFAULTS[1]), 2);
		assertThat(setup.color()).isEqualTo(PlayerColors.DEFAULTS[2]);
		setup.nextColor();
		assertThat(setup.color()).isEqualTo(PlayerColors.DEFAULTS[3]);
		// Colours other players have are skipped.
		setup.nextColor();
		assertThat(setup.color()).isEqualTo(PlayerColors.DEFAULTS[2]);
		setup.previousColor();
		assertThat(setup.color()).isEqualTo(PlayerColors.DEFAULTS[3]);
	}

	@Test
	void quickSetupBuildsAValidProfile() {
		var setup = new QuickSetup(List.of(), "Host", List.of(PlayerColors.DEFAULTS[0]), 1);
		UUID id = UUID.randomUUID();
		GuestProfile built = setup.build(id, "Moss", "pad-model-3");
		assertThat(built.id()).isEqualTo(id);
		assertThat(built.name()).isEqualTo("Moss");
		assertThat(built.preferredControllerGuid()).isEqualTo("pad-model-3");
		assertThat(built.color()).isEqualTo(PlayerColors.DEFAULTS[1]);
		assertThat(built.skin()).isEqualTo(QuickSetup.SKINS.get(1));
		assertThat(built.controls()).isEqualTo(ControlScheme.BEDROCK);
		assertThat(built.settings()).isEqualTo(PersonalSettings.DEFAULTS);
	}

	@Test
	void hostProfileWithoutControllerLoads() throws Exception {
		Path file = dir.resolve("profiles.json");
		Files.writeString(file, """
				{"schemaVersion":3,"profiles":[],"host":{"controls":{},"color":4161488}}
				""");
		HostProfile host = new ProfileStore(file).loadFile().host();
		assertThat(host.preferredControllerGuid()).isNull();
		new ProfileStore(file).saveFile(new ProfileFile(List.of(), host));
		// Saving keeps it absent-equivalent ("controller": null); Plan 5b1's reader ignores the unknown member.
		assertThat(new ProfileStore(file).loadFile().host()).isEqualTo(host);
	}

	@Test
	void hostControllerRoundTrips() throws Exception {
		Path file = dir.resolve("profiles.json");
		HostProfile host = HostProfile.DEFAULTS.withController("pad-model-7");
		new ProfileStore(file).saveFile(new ProfileFile(List.of(), host));
		assertThat(new ProfileStore(file).loadFile().host()).isEqualTo(host);
		assertThat(host.preferredControllerGuid()).isEqualTo("pad-model-7");
	}
}
