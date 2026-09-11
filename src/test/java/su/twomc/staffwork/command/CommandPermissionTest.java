package su.twomc.staffwork.command;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.TMCStaffWork;
import su.twomc.staffwork.message.MessageService;

class CommandPermissionTest {
    @Test
    void rejectsAdministrativeActionOnServerSideWithoutPermission() {
        TMCStaffWork plugin = mock(TMCStaffWork.class);
        MessageService messages = mock(MessageService.class);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        when(plugin.isReady()).thenReturn(true);
        when(sender.hasPermission(Permissions.RELOAD)).thenReturn(false);
        StaffWorkCommand handler = new StaffWorkCommand(plugin, messages);

        handler.onCommand(sender, command, "staffwork", new String[] {"reload"});

        verify(messages).send(sender, "error.no-permission");
        verify(plugin, never()).reloadSafeSettings();
    }
}
