package dev.hearthside.fabric.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.input.Action;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.GameplayIntent;
import dev.hearthside.core.input.GamepadMapping;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuAction;
import dev.hearthside.core.input.MenuIntent;
import dev.hearthside.core.input.SlotGeometry;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.mixin.ContainerScreenAccessor;
import dev.hearthside.fabric.client.mixin.KeyMappingAccessor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;

/**
 * Drives this client's Minecraft from one controller: vanilla key mappings, analog movement and
 * camera in the world; a virtual cursor with Bedrock-style slot snapping in menus. Call
 * {@link #onFrame()} once per rendered frame on the render thread.
 */
public final class InputApplier {
	/** Entity.turn multiplies its arguments by this. */
	private static final double TURN_SCALE = 0.15;
	private static final double SNAP_RADIUS_GUI = 12;
	private static final int LEFT = 0, RIGHT = 1, PRESS = 1, RELEASE = 0;

	private final Minecraft minecraft;
	private volatile ControlScheme scheme = ControlScheme.BEDROCK;
	private volatile InputFrame latest;
	private InputFrame previous = InputFrame.NEUTRAL;
	private long lastFrameNanos;
	private boolean inMenu;
	/** Key mappings this applier is currently holding down. */
	private final Set<Action> holding = EnumSet.noneOf(Action.class);

	public InputApplier(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	/** Applies a scheme; also sets this client's toggle-sneak and auto-jump options. Render thread. */
	public void setScheme(ControlScheme newScheme) {
		this.scheme = newScheme;
		minecraft.options.toggleCrouch().set(newScheme.toggleSneak());
		minecraft.options.autoJump().set(newScheme.autoJump());
	}

	public ControlScheme scheme() {
		return scheme;
	}

	/** Latest controller state; any thread. */
	public void accept(InputFrame frame) {
		this.latest = frame;
	}

	public void onFrame() {
		InputFrame frame = latest;
		if (frame == null) {
			return;
		}
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : (now - lastFrameNanos) / 1e9f;
		lastFrameNanos = now;
		if (!isNeutral(frame) || !isNeutral(previous)) {
			// Controller input is input: without this a satellite, which never sees OS input, is
			// throttled to the "away from keyboard" frame rate.
			minecraft.getFramerateLimitTracker().onInputReceived();
		}

		Screen screen = minecraft.gui.screen();
		if (screen == null) {
			if (inMenu) {
				HearthsideMod.LOGGER.info("Hearthside controller: world mode");
			}
			inMenu = false;
			applyGameplay(GamepadMapping.gameplay(scheme, previous, frame, dt));
		} else {
			if (!inMenu) {
				HearthsideMod.LOGGER.info("Hearthside controller: menu mode ({})", screen.getClass().getSimpleName());
				inMenu = true;
				releaseHeld();
				PadMovement.clear();
				VirtualCursor.ensurePlaced(minecraft.getWindow());
			}
			applyMenu(screen, GamepadMapping.menu(scheme, previous, frame, dt));
		}
		previous = frame;
	}

	/** Lets go of everything this controller is holding. */
	public void release() {
		releaseHeld();
		PadMovement.clear();
		latest = null;
		previous = InputFrame.NEUTRAL;
	}

	private void applyGameplay(GameplayIntent intent) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		Options o = minecraft.options;
		Map<Action, KeyMapping> heldMappings = new EnumMap<>(Map.of(
				Action.JUMP, o.keyJump, Action.SNEAK, o.keyShift, Action.SPRINT, o.keySprint,
				Action.ATTACK, o.keyAttack, Action.USE, o.keyUse, Action.PLAYER_LIST, o.keyPlayerList));
		heldMappings.forEach((action, mapping) -> {
			boolean want = intent.held().contains(action);
			if (want && holding.add(action)) {
				mapping.setDown(true);
			} else if (!want && holding.remove(action)) {
				mapping.setDown(false);
			}
		});

		Map<Action, KeyMapping> clickMappings = Map.of(
				Action.INVENTORY, o.keyInventory, Action.DROP, o.keyDrop, Action.SWAP_OFFHAND, o.keySwapOffhand,
				Action.PERSPECTIVE, o.keyTogglePerspective, Action.PICK_BLOCK, o.keyPickItem, Action.CHAT, o.keyChat);
		clickMappings.forEach((action, mapping) -> {
			if (intent.pressed().contains(action)) {
				KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
				accessor.hearthside$setClickCount(accessor.hearthside$getClickCount() + 1);
			}
		});

		if (intent.pressed().contains(Action.CRAFTING)) {
			player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
			minecraft.gui.setScreen(new InventoryScreen(player));
		}
		if (intent.pressed().contains(Action.PAUSE)) {
			minecraft.pauseGame(false);
		}
		if (intent.hotbarDelta() != 0) {
			var inventory = player.getInventory();
			inventory.setSelectedSlot(Math.floorMod(inventory.getSelectedSlot() + intent.hotbarDelta(), 9));
		}
		if (intent.yawDegrees() != 0 || intent.pitchDegrees() != 0) {
			player.turn(intent.yawDegrees() / TURN_SCALE, intent.pitchDegrees() / TURN_SCALE);
		}
		if (intent.moveX() != 0 || intent.moveY() != 0) {
			PadMovement.set(intent.moveX(), intent.moveY());
		} else {
			PadMovement.clear();
		}
	}

	private void applyMenu(Screen screen, MenuIntent intent) {
		Window window = minecraft.getWindow();
		long handle = window.handle();
		double oldX = VirtualCursor.x(), oldY = VirtualCursor.y();
		VirtualCursor.moveTo(oldX + intent.cursorDx(), oldY + intent.cursorDy(), window);

		if (screen instanceof AbstractContainerScreen<?> container) {
			double toGui = (double) window.getGuiScaledWidth() / window.getScreenWidth();
			List<SlotGeometry.Point> centres = slotCentres(container);
			double gx = VirtualCursor.x() * toGui, gy = VirtualCursor.y() * toGui;
			Optional<SlotGeometry.Point> target = (intent.hopX() != 0 || intent.hopY() != 0)
					? SlotGeometry.hop(centres, gx, gy, intent.hopX(), intent.hopY())
					: (intent.cursorDx() == 0 && intent.cursorDy() == 0)
							? SlotGeometry.snap(centres, gx, gy, SNAP_RADIUS_GUI)
							: Optional.empty();
			target.ifPresent(p -> VirtualCursor.moveTo(p.x() / toGui, p.y() / toGui, window));
		}

		double x = VirtualCursor.x(), y = VirtualCursor.y();
		if (x != oldX || y != oldY) {
			minecraft.mouseHandler.onMove(handle, x, y, x - oldX, y - oldY);
		}
		button(handle, intent, MenuAction.PICK_PLACE, LEFT, 0);
		button(handle, intent, MenuAction.TAKE_HALF, RIGHT, 0);
		button(handle, intent, MenuAction.QUICK_MOVE, LEFT, InputConstants.MOD_SHIFT);
		if (intent.scroll() != 0) {
			minecraft.mouseHandler.onScroll(handle, 0, intent.scroll());
		}
		if (intent.down().contains(MenuAction.CLOSE)) {
			screen.onClose();
		}
	}

	private void button(long handle, MenuIntent intent, MenuAction action, int button, int modifiers) {
		if (intent.down().contains(action)) {
			minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(button, modifiers), PRESS);
		}
		if (intent.up().contains(action)) {
			minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(button, modifiers), RELEASE);
		}
	}

	private static List<SlotGeometry.Point> slotCentres(AbstractContainerScreen<?> screen) {
		ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
		List<SlotGeometry.Point> centres = new ArrayList<>();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.isActive()) {
				centres.add(new SlotGeometry.Point(accessor.hearthside$leftPos() + slot.x + 8, accessor.hearthside$topPos() + slot.y + 8));
			}
		}
		return centres;
	}

	private static boolean isNeutral(InputFrame f) {
		return f.buttons() == 0 && f.leftX() == 0 && f.leftY() == 0 && f.rightX() == 0 && f.rightY() == 0
				&& f.leftTrigger() == 0 && f.rightTrigger() == 0;
	}

	private void releaseHeld() {
		Options o = minecraft.options;
		Map<Action, KeyMapping> mappings = Map.of(Action.JUMP, o.keyJump, Action.SNEAK, o.keyShift, Action.SPRINT, o.keySprint,
				Action.ATTACK, o.keyAttack, Action.USE, o.keyUse, Action.PLAYER_LIST, o.keyPlayerList);
		for (Action action : holding) {
			KeyMapping mapping = mappings.get(action);
			if (mapping != null) {
				mapping.setDown(false);
			}
		}
		holding.clear();
	}
}
