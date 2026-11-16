package com.kingpixel.cobbleutils.command.base;

import com.cobblemon.mod.common.command.argument.PartySlotArgumentType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.cobblemon.mod.common.pokemon.Nature;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Carlos Varas Alonso - 26/07/2024 14:14
 */
public class PokeShout implements Command<CommandSource> {
  public static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                              LiteralArgumentBuilder<ServerCommandSource> base) {
    dispatcher.register(
      base
        .requires(source -> PermissionApi.hasPermission(source, List.of("cobbleutils.pokeshoutplus",
          "cobbleutils" +
            ".user"), 2))
        .then(
          CommandManager.argument("slot", PartySlotArgumentType.Companion.partySlot())
            .executes(context -> {
              if (!context.getSource().isExecutedByPlayer()) {
                CobbleUtils.LOGGER_RAW.error("This command can only be executed by a player");
                return 0;
              }
              Pokemon pokemon = PartySlotArgumentType.Companion.getPokemon(context, "slot");
              ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
              if (player == null) return 0;
              if (pokemon == null) {
                PlayerUtils.sendMessage(player,
                  CobbleUtils.language.getMessageNoPokemon(),
                  CobbleUtils.config.getPrefix(),
                  TypeMessage.CHAT
                );
                return 0;
              }
              long cooldown = cooldowns.getOrDefault(player.getUuid(), 0L);
              if (PlayerUtils.isCooldown(cooldown)) {
                PlayerUtils.sendMessage(player, CobbleUtils.language.getMessageCooldownCommand()
                    .replace("%cooldown%", String.valueOf(PlayerUtils.getCooldown(cooldown))),
                  CobbleUtils.config.getPrefix(),
                  TypeMessage.CHAT
                );
                return 0;
              }
              cooldowns.put(player.getUuid(),
                System.currentTimeMillis() + CobbleUtils.config.getCooldownpokeshout().toMillis());
              if (CobbleUtils.config.isRedisMessaging()) {
                Utils.broadcastMessage(getMessageString(player, pokemon));
              } else {
                Utils.broadcastMessage(getMessage(player, pokemon));
              }
              return 1;
            })));
  }

  @Override
  public int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
    return 0;
  }

  public static String getMessageString(ServerPlayerEntity player, Pokemon pokemon) {
    String baseMessage = CobbleUtils.language.getMessagePokeShout();
    String playerName = player.getGameProfile().getName();
    String messageContent = PokemonUtils.replace(baseMessage, pokemon).replace("%player%", playerName);

      String hoverInfo = String.join("<br>", getLore(pokemon))
              .replace("'", "\\'");
    messageContent = "<hover:show_text:'" + hoverInfo + "'>" + messageContent + "</hover>";

    return messageContent;
  }

  public static Text getMessage(ServerPlayerEntity player, Pokemon pokemon) {
    String baseMessage = CobbleUtils.language.getMessagePokeShout();
    String playerName = player.getGameProfile().getName();
    String messageContent = PokemonUtils.replace(baseMessage, pokemon).replace("%player%", playerName);

    return AdventureTranslator.toNativeComponent(messageContent)
      .setStyle(Style.EMPTY
        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                AdventureTranslator.toNative(String.join("\n", getLore(pokemon))))));
  }

    private static List<String> getLore(Pokemon pokemon) {
        List<String> lore = new ArrayList<>(PokemonUtils.replaceLore(pokemon));
        lore.addAll(getExtraLore(pokemon));
        return lore;
    }

    private static List<String> getExtraLore(Pokemon pokemon) {
        List<String> extra = new ArrayList<>();
        if (PokemonUtils.isEgg(pokemon)) return extra;

        if (isAlpha(pokemon)) {
            extra.add("&c&l★ Alpha");
        }

        extra.add("&7Ball: &f" + PokemonUtils.getPokeBallTranslate(pokemon.getCaughtBall()));

        String aspects = pokemon.getAspects().stream()
                .filter(a -> !isBoringAspect(a))
                .collect(Collectors.joining(", "));
        if (!aspects.isEmpty()) {
            extra.add("&7Aspects: &f" + aspects);
        }

        String marks = pokemon.getMarks().stream()
                .map(m -> m.getIdentifier().getPath())
                .collect(Collectors.joining(", "));
        if (!marks.isEmpty()) {
            extra.add("&7Marks: &f" + marks);
        }

        Nature minted = pokemon.getMintedNature();
        if (minted != null && !minted.equals(pokemon.getNature())) {
            extra.add("&7Minted Nature: &f" + PokemonUtils.getNatureTranslate(minted));
        }

        String hyper = pokemon.getIvs().getHyperTrainedIVs().keySet().stream()
                .map(PokemonUtils::getStatTranslate)
                .collect(Collectors.joining(", "));
        if (!hyper.isEmpty()) {
            extra.add("&7Hyper Trained: &f" + hyper);
        }

        return extra;
    }

    private static boolean isAlpha(Pokemon pokemon) {
        return pokemon.getAspects().contains("alpha") || pokemon.getPersistentData().getBoolean("alpha");
    }

    private static boolean isBoringAspect(String aspect) {
        String a = aspect.toLowerCase();
        return a.equals("male") || a.equals("female") || a.equals("genderless")
                || a.equals("milkable") || a.equals("family") || a.startsWith("bucket");
    }

}