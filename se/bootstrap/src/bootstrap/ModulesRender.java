package bootstrap;

import bootstrap.wire.ModuleFold;
import bootstrap.wire.Toggle;
import java.util.ArrayList;
import java.util.List;
import platform.lang.Messages;

/**
 * Pure rendering for {@code /se modules} (ADR-0047): the frozen {@link ModuleFold.Report} &rarr; chat lines
 * through the {@link Messages} catalogue. Server-free (the report is a plain data snapshot), so it is unit-pinned.
 * BOOT gates render their wire-time verdict (a restart-time decision); LIVE gates read their supplier NOW, since
 * a live toggle can flip between boot and this render (the listeners stay registered either way).
 */
final class ModulesRender {

    private ModulesRender() {
    }

    static List<String> lines(ModuleFold.Report report, Messages messages) {
        List<String> out = new ArrayList<>();
        out.add(messages.format("command.modules.header", "COUNT", report.modules().size()));
        for (ModuleFold.ModuleReport module : report.modules()) {
            out.add(messages.format("command.modules.name", "NAME", module.name(),
                    "TOGGLE", toggle(module, messages)));
            addLine(out, messages, "command.modules.listeners", module.listeners());
            addLine(out, messages, "command.modules.skipped", module.skipped());
            addLine(out, messages, "command.modules.installs", module.installs());
            addLine(out, messages, "command.modules.commands", module.commands());
            addLine(out, messages, "command.modules.mints", module.mintTypes());
            addLine(out, messages, "command.modules.menus", module.menuNames());
            if (module.playerStores() > 0) {
                out.add(messages.format("command.modules.stores", "N", module.playerStores()));
            }
            addLine(out, messages, "command.modules.stops", module.stops());
        }
        return out;
    }

    private static void addLine(List<String> out, Messages messages, String key, List<String> list) {
        if (!list.isEmpty()) {
            out.add(messages.format(key, "LIST", String.join(", ", list)));
        }
    }

    /** The toggle fragment: always-on, a boot gate (wire-time verdict), or a live gate (read now). */
    private static String toggle(ModuleFold.ModuleReport module, Messages messages) {
        Toggle t = module.toggle();
        return switch (t.depth()) {
            case NONE -> messages.fragment("command.modules.toggle-always");
            case BOOT -> messages.fragment("command.modules.toggle-boot",
                    "KEY", t.key(), "STATE", state(module.wired(), messages));
            case LIVE -> messages.fragment("command.modules.toggle-live",
                    "KEY", t.key(), "STATE", state(t.enabled().getAsBoolean(), messages));
        };
    }

    private static String state(boolean on, Messages messages) {
        return messages.fragment(on ? "command.modules.state-on" : "command.modules.state-off");
    }
}
