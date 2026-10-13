package dev.hearthside.fabric.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.hearthside.core.input.Action;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.DropGesture;
import dev.hearthside.core.input.FreshInput;
import dev.hearthside.core.input.GameplayIntent;
import dev.hearthside.core.input.GamepadMapping;
import dev.hearthside.core.input.HeldSuppression;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.MenuAction;
import dev.hearthside.core.input.MenuIntent;
import dev.hearthside.core.input.PointerHandOff;
import dev.hearthside.core.input.SlotGeometry;
import dev.hearthside.fabric.HearthsideMod;
import dev.hearthside.fabric.client.menu.PadScreen;
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
	/** A guest stops acting on input this old (the host may have stalled). */
	private static final long MAX_INPUT_AGE_NANOS = 300_000_000L;
	private static final int PRESS = 1, RELEASE = 0;
	/** InventoryScreen's widthTooNarrow threshold. */
	private static final int RECIPE_BOOK_MIN_GUI_WIDTH = 379;

	private final Minecraft minecraft;
	private final FreshInput input = new FreshInput(System::nanoTime, MAX_INPUT_AGE_NANOS);
	private final HeldSuppression suppression = new HeldSuppression();
	private volatile ControlScheme scheme = ControlScheme.BEDROCK;
	private InputFrame previous = InputFrame.NEUTRAL;
	/** Last frame used for gameplay, after suppression; edges are computed against it. */
	private InputFrame previousGameplay = InputFrame.NEUTRAL;
	private long lastFrameNanos;
	private boolean inMenu;
	/** Key mappings this applier is currently holding down. */
	private final Set<Action> holding = EnumSet.noneOf(Action.class);
	/** Drop: a tap throws one, holding throws the rest of the stack. */
	private final DropGesture drop = new DropGesture();
	/** Player 1's screens are shared with the OS mouse: whichever was used last drives them. */
	private boolean sharesWithMouse;
	/**
	 * The OS mouse drives the open screen ({@link RealMouse#handOff()}); the pointer waits (hidden) until the stick, D-pad
	 * or A takes it back.
	 */
	private boolean mouseDriving;

	public InputApplier(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	/** Player 1's applier, in the world: the OS mouse may take over a screen from the pointer, and give it back. */
	public void shareWithMouse(boolean share) {
		sharesWithMouse = share;
		if (!share) {
			mouseDriving = false;
		}
	}

	/** Whether the OS mouse, not this controller's pointer, drives the open screen. */
	public boolean mouseDriving() {
		return mouseDriving && inMenu;
	}

	/** Uses {@code newScheme} for bindings and look settings. Does not touch game options. */
	public void setScheme(ControlScheme newScheme) {
		this.scheme = newScheme;
	}

	/** Applies the scheme's toggle-sneak and auto-jump to this client's options. Render thread. */
	public void applyOptions() {
		minecraft.options.toggleCrouch().set(scheme.toggleSneak());
		minecraft.options.autoJump().set(scheme.autoJump());
	}

	public ControlScheme scheme() {
		return scheme;
	}

	/** Latest controller state; any thread. */
	public void accept(InputFrame frame) {
		input.accept(frame);
	}

	public void onFrame() {
		Optional<InputFrame> current = input.current();
		if (current.isEmpty()) {
			PadPlay.set(false);
			return;
		}
		InputFrame frame = current.get();
		ButtonGuideRenderer.onFrame(frame);
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : (now - lastFrameNanos) / 1e9f;
		lastFrameNanos = now;
		if (!isNeutral(frame) || !isNeutral(previous)) {
			// Controller input is input: without this a satellite, which never sees OS input, is
			// throttled to the "away from keyboard" frame rate.
			minecraft.getFramerateLimitTracker().onInputReceived();
		}
		try {
			Screen screen = minecraft.gui.screen();
			if (screen == null) {
				if (sharesWithMouse && used(frame)) {
					// Playing with the controller: a screen that opens now is the controller's (Esc after mouse look, the mouse's).
					RealMouse.handOff().padUsed(now);
				}
				if (inMenu) {
					HearthsideMod.LOGGER.info("Hearthside controller: world mode");
					// Buttons still held from the menu (B to close, A on a widget) must not act in the world.
					suppression.suppressHeld(frame);
					previousGameplay = InputFrame.NEUTRAL;
				}
				inMenu = false;
				PadPlay.set(true);
				InputFrame filtered = suppression.filter(frame);
				applyGameplay(GamepadMapping.gameplay(scheme, previousGameplay, filtered, dt), now);
				previousGameplay = filtered;
			} else {
				if (!inMenu) {
					HearthsideMod.LOGGER.info("Hearthside controller: menu mode ({})", screen.getClass().getSimpleName());
					inMenu = true;
					PadPlay.set(false);
					releaseHeld();
					drop.step(false, now);
					PadMovement.clear();
					VirtualCursor.centre(minecraft.getWindow());
				}
				if (screen instanceof PadScreen padScreen && minecraft.level != null) {
					// Hearthside's screens in the world (the pause menu) take this controller by focus: no pointer.
					padScreen.padInput(0, frame, now);
				} else {
					applyMenu(screen, GamepadMapping.menu(scheme, previous, frame, dt), now);
				}
			}
		} finally {
			// Even if applying failed, never replay the same button edge on the next frame.
			previous = frame;
		}
	}

	/** Lets go of everything this controller is holding. */
	public void release() {
		PadPlay.set(false);
		releaseHeld();
		drop.step(false, System.nanoTime());
		PadMovement.clear();
		input.clear();
		previous = InputFrame.NEUTRAL;
		previousGameplay = InputFrame.NEUTRAL;
	}

	private void applyGameplay(GameplayIntent intent, long now) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		heldMappings().forEach((action, mapping) -> {
			boolean want = intent.held().contains(action);
			if (want && holding.add(action)) {
				mapping.setDown(true);
			} else if (!want && holding.remove(action)) {
				mapping.setDown(false);
			}
		});

		Options o = minecraft.options;
		// Attack and use are held above and also clicked here, like a mouse button: vanilla starts
		// an attack or a use from a click and only continues it from the held state.
		Map<Action, KeyMapping> clickMappings = Map.of(
				Action.ATTACK, o.keyAttack, Action.USE, o.keyUse, Action.INVENTORY, o.keyInventory, Action.SWAP_OFFHAND, o.keySwapOffhand,
				Action.PERSPECTIVE, o.keyTogglePerspective, Action.PICK_BLOCK, o.keyPickItem, Action.CHAT, o.keyChat);
		clickMappings.forEach((action, mapping) -> {
			if (intent.pressed().contains(action)) {
				click(mapping);
			}
		});
		// One item through vanilla's Q (its spectator check and drop-spam throttle); the rest of the stack as Ctrl+Q does.
		drop.step(intent.held().contains(Action.DROP), now).ifPresent(d -> {
			if (d == DropGesture.Drop.ONE) {
				click(o.keyDrop);
			} else {
				dropStack(player);
			}
		});
		if (intent.pressed().contains(Action.DROP_STACK)) {
			dropStack(player);
		}

		if (intent.pressed().contains(Action.CRAFTING)) {
			// A split-screen window is often narrower than vanilla's 379 GUI px, where the recipe book
			// is drawn over the inventory instead of beside it; only show it where it fits.
			boolean fits = minecraft.getWindow().getGuiScaledWidth() >= RECIPE_BOOK_MIN_GUI_WIDTH;
			player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, fits);
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

	/** Presses a key mapping once, as a key press would (vanilla consumes the click on its next tick). */
	private static void click(KeyMapping mapping) {
		KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
		accessor.hearthside$setClickCount(accessor.hearthside$getClickCount() + 1);
	}

	/** Throws the selected stack (what's left of it), as Ctrl+Q does. */
	private void dropStack(LocalPlayer player) {
		if (!player.isSpectator() && minecraft.gameMode != null) {
			minecraft.gameMode.dropItem(player, true);
		}
	}

	private void applyMenu(Screen screen, MenuIntent intent, long now) {
		Window window = minecraft.getWindow();
		long handle = window.handle();
		if (sharesWithMouse) {
			// The screen is whichever player 1 used last: the mouse once it really moves or clicks (RealMouse), the
			// controller once it points or clicks.
			boolean points = intent.cursorDx() != 0 || intent.cursorDy() != 0 || intent.hopX() != 0 || intent.hopY() != 0
					|| intent.down().contains(MenuAction.PICK_PLACE) || intent.down().contains(MenuAction.TAKE_HALF)
					|| intent.down().contains(MenuAction.QUICK_MOVE);
			PointerHandOff handOff = RealMouse.handOff();
			if (points) {
				if (handOff.owner() == PointerHandOff.Owner.MOUSE) {
					// The controller takes over from where the mouse left off.
					VirtualCursor.moveTo(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos(), window);
				}
				handOff.padUsed(now);
			}
			mouseDriving = handOff.owner() == PointerHandOff.Owner.MOUSE;
		}
		if (mouseDriving) {
			// Nothing of the pointer's; a button let go still goes out, and the right stick still scrolls, B still closes.
			releases(handle, intent);
			scrollAndClose(screen, handle, intent);
			return;
		}
		VirtualCursor.moveTo(VirtualCursor.x() + intent.cursorDx(), VirtualCursor.y() + intent.cursorDy(), window);

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

		// Opening a screen re-syncs MouseHandler to the real OS cursor; every frame, put it back on
		// the virtual cursor so hover highlights and clicks land where the player sees the pointer.
		double x = VirtualCursor.x(), y = VirtualCursor.y();
		double mouseX = minecraft.mouseHandler.xpos(), mouseY = minecraft.mouseHandler.ypos();
		if (x != mouseX || y != mouseY) {
			SyntheticMouse.run(() -> minecraft.mouseHandler.onMove(handle, x, y, x - mouseX, y - mouseY));
		}
		button(handle, intent, MenuAction.PICK_PLACE, InputConstants.MOUSE_BUTTON_LEFT, 0);
		button(handle, intent, MenuAction.TAKE_HALF, InputConstants.MOUSE_BUTTON_RIGHT, 0);
		button(handle, intent, MenuAction.QUICK_MOVE, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOD_SHIFT);
		scrollAndClose(screen, handle, intent);
	}

	private void scrollAndClose(Screen screen, long handle, MenuIntent intent) {
		if (intent.scroll() != 0) {
			SyntheticMouse.run(() -> minecraft.mouseHandler.onScroll(handle, 0, intent.scroll()));
		}
		if (intent.down().contains(MenuAction.CLOSE) && minecraft.gui.screen() == screen && screen.shouldCloseOnEsc()) {
			screen.onClose();
		}
	}

	/** Lets go of any mouse button the controller pressed and has now released. */
	private void releases(long handle, MenuIntent intent) {
		release(handle, intent, MenuAction.PICK_PLACE, InputConstants.MOUSE_BUTTON_LEFT, 0);
		release(handle, intent, MenuAction.TAKE_HALF, InputConstants.MOUSE_BUTTON_RIGHT, 0);
		release(handle, intent, MenuAction.QUICK_MOVE, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOD_SHIFT);
	}

	private void release(long handle, MenuIntent intent, MenuAction action, int button, int modifiers) {
		if (intent.up().contains(action)) {
			SyntheticMouse.run(() -> minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(button, modifiers), RELEASE));
		}
	}

	/** Whether the controller is being used: a button, a trigger or a stick past the dead zone. */
	private boolean used(InputFrame f) {
		float zone = scheme.deadZone();
		return f.buttons() != 0 || f.leftTrigger() > 0.3f || f.rightTrigger() > 0.3f
				|| Math.hypot(f.leftX(), f.leftY()) > zone || Math.hypot(f.rightX(), f.rightY()) > zone;
	}

	private void button(long handle, MenuIntent intent, MenuAction action, int button, int modifiers) {
		if (intent.down().contains(action)) {
			SyntheticMouse.run(() -> minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(button, modifiers), PRESS));
		}
		if (intent.up().contains(action)) {
			SyntheticMouse.run(() -> minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(button, modifiers), RELEASE));
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

	private Map<Action, KeyMapping> heldMappings() {
		Options o = minecraft.options;
		Map<Action, KeyMapping> mappings = new EnumMap<>(Action.class);
		mappings.put(Action.JUMP, o.keyJump);
		mappings.put(Action.SNEAK, o.keyShift);
		mappings.put(Action.SPRINT, o.keySprint);
		mappings.put(Action.ATTACK, o.keyAttack);
		mappings.put(Action.USE, o.keyUse);
		mappings.put(Action.PLAYER_LIST, o.keyPlayerList);
		return mappings;
	}

	private void releaseHeld() {
		Map<Action, KeyMapping> mappings = heldMappings();
		for (Action action : holding) {
			mappings.get(action).setDown(false);
		}
		holding.clear();
	}

	private static boolean isNeutral(InputFrame f) {
		return f.buttons() == 0 && f.leftX() == 0 && f.leftY() == 0 && f.rightX() == 0 && f.rightY() == 0
				&& f.leftTrigger() == 0 && f.rightTrigger() == 0;
	}
}
