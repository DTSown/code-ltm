package client;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import javax.swing.AbstractButton;
import javax.swing.JComponent;

/** One binding per control, including controls added later to the same screen. */
public final class UiSounds {
    private static final String BOUND = UiSounds.class.getName();
    private UiSounds() { }

    public static void install(Component root, SoundManager sound) {
        if (!(root instanceof JComponent)) { return; }
        JComponent component = (JComponent) root;
        if (component.getClientProperty(BOUND) != null) { return; }
        component.putClientProperty(BOUND, sound);
        if (component instanceof AbstractButton && !(component instanceof SoundToggle)
                && (component.getName() == null || !component.getName().startsWith("MOVE_"))) {
            AbstractButton button = (AbstractButton) component;
            button.addActionListener(event -> {
                if (button.isEnabled()) { sound.play(SoundManager.Effect.CLICK); }
            });
        }
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) { install(child, sound); }
            container.addContainerListener(new ContainerAdapter() {
                @Override public void componentAdded(ContainerEvent event) { install(event.getChild(), sound); }
            });
        }
    }
}
