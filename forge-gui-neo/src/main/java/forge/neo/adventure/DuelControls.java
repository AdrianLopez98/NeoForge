package forge.neo.adventure;

import forge.neo.NeoShortcuts;
import forge.neo.NeoText;
import forge.neo.match.NeoMatchUI;
import forge.neo.ui.CardZoom;
import forge.neo.ui.PauseMenu;
import forge.neo.ui.SettingsPanel;
import forge.neo.ui.TableScreen;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * el teclado y el menu de pausa del duelo del Adventure, como en una
 * partida normal de NeoForge.
 *
 * <p>En NeoForge eso lo pone la ventana principal ({@code NeoApp}), que aqui no
 * existe: sin esto, Escape no hacia nada (reportado jugando). Copia su
 * comportamiento: Escape cierra primero la carta ampliada, luego vuelve al
 * dialogo apartado y si no abre la pausa; y los atajos de partida de
 * {@link NeoShortcuts}, con la tecla que tengas configurada.
 *
 * <p>La pausa va <b>sin "Reiniciar"</b> (repetir el duelo seria volver a barajar
 * contra el mismo enemigo) y su "Salir" dice lo que hace aqui: rendirse y volver
 * al mapa.
 */
final class DuelControls {

    private DuelControls() {
    }

    private static final Set<NeoShortcuts.Action> HELD = EnumSet.noneOf(NeoShortcuts.Action.class);

    static void install(final Scene scene, final TableScreen table, final NeoMatchUI ui) {
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                if (table.isZoomShowing()) {
                    table.hideZoom();
                } else if (table.getOverlay().isPeeking()) {
                    table.getOverlay().setPeeking(false);
                } else if (table.getMenuOverlay().isShowing()) {
                    table.getMenuOverlay().hide();
                } else {
                    openPause(table, ui);
                }
                ev.consume();
                return;
            }
            if (shortcut(ev, scene, table, ui)) {
                ev.consume();
            }
        });
        scene.setOnKeyReleased(ev -> HELD.clear());
    }

    private static void openPause(final TableScreen table, final NeoMatchUI ui) {
        final PauseMenu menu = new PauseMenu(new PauseMenu.Actions() {
            @Override
            public void resume() {
                table.getMenuOverlay().hide();
            }

            @Override
            public void restart() {
                table.getMenuOverlay().hide();
            }

            @Override
            public void quitToMenu() {
                table.getMenuOverlay().hide();
                ui.leaveMatch(NeoMatchUI.Exit.MENU);
            }
        }, host(table, ui));
        // Sin "Reiniciar", y "Salir" dice lo que hace aqui.
        final String restart = NeoText.get("pause.restart");
        final String quit = NeoText.get("pause.quit");
        for (final Node n : menu.lookupAll(".button")) {
            if (n instanceof Labeled) {
                final Labeled l = (Labeled) n;
                if (restart.equals(l.getText())) {
                    l.setVisible(false);
                    l.setManaged(false);
                } else if (quit.equals(l.getText())) {
                    l.setText(NeoText.get("adventure.concede"));
                }
            }
        }
        table.getMenuOverlay().show(menu);
    }

    private static SettingsPanel.Host host(final TableScreen table, final NeoMatchUI ui) {
        return new SettingsPanel.Host() {
            @Override
            public void setAutoPayMana(final boolean on) {
                ui.setAutoPayMana(on);
            }

            @Override
            public void setPauseMode(final int mode) {
                ui.setPauseMode(mode);
            }

            @Override
            public void setAiSpeed(final int hundredths) {
                ui.setAiSpeed(hundredths);
            }

            @Override
            public void applyScale() {
                final Scene s = table.getScene();
                if (s != null && s.getRoot() != null) {
                    s.getRoot().setStyle(forge.neo.ui.UiScale.rootStyle(s.getHeight()));
                }
            }

            @Override
            public boolean isFullScreen() {
                final Scene s = table.getScene();
                return s != null && s.getWindow() instanceof javafx.stage.Stage
                        && ((javafx.stage.Stage) s.getWindow()).isFullScreen();
            }

            @Override
            public void setFullScreen(final boolean on) {
                final Scene s = table.getScene();
                if (s != null && s.getWindow() instanceof javafx.stage.Stage) {
                    ((javafx.stage.Stage) s.getWindow()).setFullScreen(on);
                }
            }

            @Override
            public double getTextZoom() {
                return table.getDetailPanel().getTextZoom();
            }

            @Override
            public void setTextZoom(final double zoom) {
                table.getDetailPanel().setTextZoom(zoom);
            }

            @Override
            public void setDraftRankingVisible(final boolean on) {
            }

            @Override
            public boolean isInMatch() {
                return true;
            }
        };
    }

    /** Los atajos de partida principales, igual que NeoApp.runShortcut. */
    private static boolean shortcut(final KeyEvent ev, final Scene scene, final TableScreen table,
                                    final NeoMatchUI ui) {
        if (ev.getTarget() instanceof javafx.scene.control.TextInputControl
                || scene.getFocusOwner() instanceof javafx.scene.control.TextInputControl) {
            return false;
        }
        final NeoShortcuts.Action action = NeoShortcuts.actionFor(ev);
        if (action == null) {
            return false;
        }
        if (!action.repeats() && !HELD.add(action)) {
            return true;
        }
        final boolean free = !table.isModalShowing();
        switch (action) {
            case PASS_PRIORITY:
                if (free && table.getPromptBanner().acknowledge()) {
                    return true;
                }
                return free && table.getActionBar().pressPrimary();
            case PASS_TURN:
                return free && ui.passTurn();
            case ALPHA_STRIKE:
                return free && ui.alphaStrike();
            case ZOOM_CARD: {
                final forge.neo.card.CardNode hovered = CardZoom.hoveredCardNode(table);
                if (hovered != null && hovered.getCard() != null) {
                    table.showZoom(hovered.getCard());
                    return true;
                }
                return false;
            }
            case GAME_LOG:
                table.getLogButton().fire();
                return true;
            case EXPAND_STACK:
                return table.toggleAllStackEntries();
            case STACK_MENU:
                return free && ui.openTopStackMenu();
            default:
                return false;
        }
    }
}
