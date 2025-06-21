package dev.tonimatas.listeners;

import dev.tonimatas.config.BotFiles;
import dev.tonimatas.systems.bank.Bank;
import dev.tonimatas.systems.bank.DailyInfo;
import dev.tonimatas.systems.roulette.Roulette;
import dev.tonimatas.systems.roulette.bets.Bet;
import dev.tonimatas.util.Messages;
import dev.tonimatas.util.TimeUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.chrono.ChronoLocalDateTime;

public class SlashCommandListener extends ListenerAdapter {
    private static final String COMMANDS_CHANNEL = "1380277341405581443";

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();

        if (checkIfUserOrGuildDoesntExist(event)) return;

        switch (command) {
            case "ping" -> executePing(event);
            case "bet" -> executeBet(event);
            case "money" -> executeMoney(event);
            case "money-top" -> executeMoneyTop(event);
            case "daily" -> executeDaily(event);
            case "pay" -> executePay(event);
            case "hi" -> executeHi(event);
            case "transactions" -> executeTransactions(event);
            case "options" -> executeOptions(event);
        }
    }

    private void executePing(SlashCommandInteractionEvent event) {
        if (checkTheUseOfCommandsInTheCommandChannel(event)) return;

        long startTime = System.currentTimeMillis();

        event.reply("Pong!").queue(response ->
                response.editOriginalFormat("Pong: %d ms", System.currentTimeMillis() - startTime).queue());
    }

    private void executeBet(SlashCommandInteractionEvent event) {
        if (checkIfUserOrGuildDoesntExist(event)) return;

        if (!event.getChannel().getId().equals(Roulette.getRoulette(event.getJDA()).getRouletteChannel().getId())) {
            MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "This command can only be run in the Roulette channel.");
            event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
            return;
        }

        Member member = event.getMember();
        String id = member.getId();

        String type = event.getSubcommandName();
        OptionMapping betOption = event.getOption("option");
        OptionMapping betMoney = event.getOption("money");

        if (type == null || betOption == null || betMoney == null) {
            MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "Invalid bet type, option or money.");
            event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
            return;
        }

        String option = betOption.getAsString();
        long money = betMoney.getAsLong();

        Bet bet = Roulette.getBet(type, id, option, money);

        if (bet == null) {
            MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "This bet type \"" + type + "\" doesn't exist.");
            event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
            return;
        }

        if (bet.isValid()) {
            if (bet.getMoney() <= BotFiles.BANK.getMoney(id)) {
                Roulette.getRoulette(event.getJDA()).addBet(bet);
                MessageEmbed embed = Messages.getDefaultEmbed(event.getJDA(), "Bet", "Your " + type + " bet has been added to the Roulette.");
                event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
            } else {
                MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "You don't have enough money.");
                event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
            }
        } else {
            MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "Invalid bet option \"" + option + "\" for \"" + type + "\".");
            event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
        }
    }

    private void executeMoney(SlashCommandInteractionEvent event) {
        if (checkTheUseOfCommandsInTheCommandChannel(event)) return;
        if (checkIfUserOrGuildDoesntExist(event)) return;

        Member member = event.getMember();
        OptionMapping option = event.getOption("user");

        if (option != null) {
            member = option.getAsMember();
        }

        if (member.getUser().isBot()) {
            MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "Bots cannot storage money.");
            event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
            return;
        }

        long money = BotFiles.BANK.getMoney(member.getId());
        MessageEmbed embed = Messages.getDefaultEmbed(event.getJDA(), "Money", member.getEffectiveName() + " has " + money + "€.");
        event.replyEmbeds(embed).queue();
    }

    private void executeMoneyTop(SlashCommandInteractionEvent event) {
        if (checkTheUseOfCommandsInTheCommandChannel(event)) return;

        MessageEmbed embed = Messages.getDefaultEmbed(event.getJDA(), "Money Top", Bank.getMoneyTopString(event.getGuild()));
        event.replyEmbeds(embed).queue();
    }

    private void executeDaily(SlashCommandInteractionEvent event) {
        if (checkTheUseOfCommandsInTheCommandChannel(event)) return;
        if (checkIfUserOrGuildDoesntExist(event)) return;

        Member member = event.getMember();

        LocalDateTime now = LocalDateTime.now();
        DailyInfo dailyInfo = BotFiles.BANK.getDaily(member.getId());

        if (now.isAfter(dailyInfo.getNext())) {
            BotFiles.BANK.addMoney(member.getId(), 100, "Daily money!");
            MessageEmbed embed = Messages.getDefaultEmbed(event.getJDA(), "Daily", "Yeah! You claimed 100€.");
            event.replyEmbeds(embed).queue();
            dailyInfo.setLast(now);
            dailyInfo.setNotified(false);
        } else {
            String formattedDate = BotFiles.BANK.getDaily(member.getId()).getNextFormatted();
            MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "You need to wait more. Your next daily will be available at " + formattedDate);
            event.replyEmbeds(embed).queue();
        }
    }

    private void executePay(SlashCommandInteractionEvent event) {
        if (checkTheUseOfCommandsInTheCommandChannel(event)) return;
        if (checkIfUserOrGuildDoesntExist(event)) return;

        Member sender = event.getMember();

        OptionMapping userOption = event.getOption("user");
        OptionMapping amountOption = event.getOption("amount");
        OptionMapping reasonOption = event.getOption("reason");
        String reason = reasonOption != null ? reasonOption.getAsString() : "No reason provided";

        if (userOption != null && amountOption != null) {
            Member receiver = userOption.getAsMember();
            long amount = amountOption.getAsLong();

            if (amount <= 0) {
                MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "You can't sent 0€.");
                event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
                return;
            }

            if (amount > BotFiles.BANK.getMoney(sender.getId())) {
                MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "Insufficient funds.");
                event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
                return;
            }

            if (receiver == null) {
                MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "Invalid receiver. Please try again later.");
                event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
                return;
            }

            long fee = (long) (amount * 0.05);

            MessageEmbed confirmation = Messages.getDefaultEmbed(event.getJDA(), "Confirm Transaction",
                    String.format("Send **%d€** to **%s**?\\nFee: **%d€**\\nTotal: **%d€**\\nReason: %s",
                            amount - fee,
                            receiver.getEffectiveName(),
                            fee,
                            amount,
                            reason));

            String confirmId = "pay:confirm:" + sender.getId() + ":" + receiver.getId() + ":" + amount + ":" + reason.replaceAll(":", "||");
            String cancelId = "pay:cancel:" + sender.getId();

            event.replyEmbeds(confirmation)
                    .addActionRow(
                            Button.success(confirmId, "✅"),
                            Button.danger(cancelId, "❌")
                    )
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void executeHi(SlashCommandInteractionEvent event) {
        if (checkTheUseOfCommandsInTheCommandChannel(event)) return;
        if (checkIfUserOrGuildDoesntExist(event)) return;

        Member member = event.getMember();

        LocalTime nowTime = LocalTime.now();
        LocalDate nowDate = LocalDate.now();

        String greeting;
        String userName = member.getEffectiveName();
        int startHourNight = isSummer(LocalDateTime.of(nowDate, nowTime)) ? 22 : 18;

        if (TimeUtils.isBetween(nowTime, 6, 0, 12, 30)) {
            greeting = "☀️ ¡Buenos días, " + userName + "! 😊";
        } else if (TimeUtils.isBetween(nowTime, 12, 31, startHourNight, 0)) {
            greeting = "🌤️ ¡Buenas tardes, " + userName + "! 😄";
        } else if (TimeUtils.isBetween(nowTime, startHourNight, 1, 23, 59) || TimeUtils.isBetween(nowTime, 0, 0, 2, 0)) {
            greeting = "🌙 ¡Buenas noches, " + userName + "! 😴";
        } else {
            greeting = "😠 ¡Duérmete, bot! Deja de saludar a estas horas...";
        }

        event.reply(greeting).queue();
    }

    private boolean isSummer(LocalDateTime date) {
        // Fechas inicio y fin de verano
        LocalDateTime summerStart = LocalDateTime.of(date.getYear(), 5, 15, 0, 0);
        LocalDateTime summerEnd = LocalDateTime.of(date.getYear(), 10, 15, 23, 59);

        return date.isAfter(ChronoLocalDateTime.from(summerStart))
                && date.isBefore(ChronoLocalDateTime.from(summerEnd));
    }

    private void executeTransactions(SlashCommandInteractionEvent event) {
        if (checkTheUseOfCommandsInTheCommandChannel(event)) return;
        if (checkIfUserOrGuildDoesntExist(event)) return;

        OptionMapping userOption = event.getOption("user");
        Member member = event.getMember();

        member = userOption != null && userOption.getAsMember() != null ? userOption.getAsMember() : member;

        if (member.getUser().isBot()) {
            MessageEmbed err = Messages.getErrorEmbed(event.getJDA(), "You can't see the transactions of a bot.");
            event.replyEmbeds(err).setEphemeral(true).queue(Messages.deleteBeforeX(10));
            return;
        }

        String transactions = Bank.getTransactionsString(member);

        MessageEmbed embed = Messages.getDefaultEmbed(event.getJDA(), "Transactions", transactions);
        event.replyEmbeds(embed).queue();
    }

    private void executeOptions(SlashCommandInteractionEvent event) {
        if (checkTheUseOfCommandsInTheCommandChannel(event)) return;

        OptionMapping dailyNotifyOption = event.getOption("daily_notify");

        if (dailyNotifyOption != null) {
            boolean dailyNotify = dailyNotifyOption.getAsBoolean();

            String userId = event.getUser().getId();

            BotFiles.SETTINGS.getSettings(userId).setNotifyDaily(dailyNotify);
            BotFiles.BANK.getDaily(userId).setNotified(false);

            String description = "Daily notifier " + (dailyNotify ? "enabled." : "disabled.");
            MessageEmbed embed = Messages.getDefaultEmbed(event.getJDA(), "Settings changed", description);
            event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
        }
    }

    private boolean checkTheUseOfCommandsInTheCommandChannel(SlashCommandInteractionEvent event) {
        if (!event.getChannel().getId().equals(COMMANDS_CHANNEL)) {
            MessageEmbed err = Messages.getErrorEmbed(event.getJDA(), "This command can only be run in the Commands channel.");
            event.replyEmbeds(err).setEphemeral(true).queue(Messages.deleteBeforeX(10));
            return true;
        }

        return false;
    }

    private boolean checkIfUserOrGuildDoesntExist(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || event.getGuild() == null) {
            MessageEmbed embed = Messages.getErrorEmbed(event.getJDA(), "Internal error. Please try again later.");
            event.replyEmbeds(embed).setEphemeral(true).queue(Messages.deleteBeforeX(10));
            return true;
        }

        return false;
    }
}