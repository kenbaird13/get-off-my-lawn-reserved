package draylar.goml.other;

import draylar.goml.GetOffMyLawn;
import draylar.goml.api.ClaimUtils;
import eu.pb4.placeholders.api.ParserContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.parsers.TagLikeParser;
import eu.pb4.placeholders.api.parsers.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApiStatus.Internal
public class PlaceholdersReg {
    public static void init() {
        var parser = TagParser.QUICK_TEXT_WITH_STF;
        Placeholders.register(GetOffMyLawn.id("claim_status"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player!");
            var claims = ClaimUtils.getClaimsAt(ctx.player().level(), ctx.player().blockPosition()).collect(Collectors.toList());
            return PlaceholderResult.value(claims.isEmpty() ? GetOffMyLawn.CONFIG.placeholderNoClaimInfo.text()
                    : draylar.goml.ui.ClaimMaintenanceGui.status(claims.getFirst().getValue()));
        });

        Placeholders.register(ResourceLocation.fromNamespaceAndPath("goml", "claim_owners"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) {
                return PlaceholderResult.invalid("No player!");
            }

            Component wildnessText = GetOffMyLawn.CONFIG.placeholderNoClaimOwners.text();
            if (arg != null) {
                wildnessText = parser.parseText(arg, ParserContext.of());
            }

            var claims = ClaimUtils.getClaimsAt(ctx.player().level(), ctx.player().blockPosition()).collect(Collectors.toList());

            if (claims.size() == 0) {
                return PlaceholderResult.value(wildnessText);
            } else {
                var claim = claims.get(0);

                List<String> owners = new ArrayList<>();
                for (UUID owner : claim.getValue().getOwners()) {
                    var profile = ctx.server().getProfileCache().get(owner);

                    if (profile.isPresent()) {
                        owners.add(profile.get().getName());
                    }
                }


                return PlaceholderResult.value(owners.size() > 0 ? Component.literal(String.join(", ", owners)) : wildnessText);
            }
        });

        Placeholders.register(ResourceLocation.fromNamespaceAndPath("goml", "claim_owners"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) {
                return PlaceholderResult.invalid("No player!");
            }

            Component wildnessText = GetOffMyLawn.CONFIG.placeholderNoClaimOwners.text();
            if (arg != null) {
                wildnessText = parser.parseText(arg, ParserContext.of());
            }

            var claims = ClaimUtils.getClaimsAt(ctx.player().level(), ctx.player().blockPosition()).collect(Collectors.toList());

            if (claims.size() == 0) {
                return PlaceholderResult.value(wildnessText);
            } else {
                var claim = claims.get(0);

                List<String> owners = new ArrayList<>();
                for (UUID owner : claim.getValue().getOwners()) {
                    var profile = ctx.server().getProfileCache().get(owner);

                    if (profile.isPresent()) {
                        owners.add(profile.get().getId().toString());
                    }
                }


                return PlaceholderResult.value(owners.size() > 0 ? Component.literal(String.join(", ", owners)) : wildnessText);
            }
        });

        Placeholders.register(ResourceLocation.fromNamespaceAndPath("goml", "claim_trusted"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) {
                return PlaceholderResult.invalid("No player!");
            }


            Component wildnessText = GetOffMyLawn.CONFIG.placeholderNoClaimTrusted.text();
            if (arg != null) {
                wildnessText = parser.parseText(arg, ParserContext.of());
            }

            var claims = ClaimUtils.getClaimsAt(ctx.player().level(), ctx.player().blockPosition()).collect(Collectors.toList());

            if (claims.size() == 0) {
                return PlaceholderResult.value(wildnessText);
            } else {
                var claim = claims.get(0);

                List<String> trusted = new ArrayList<>();
                for (UUID owner : claim.getValue().getTrusted()) {
                    var profile = ctx.server().getProfileCache().get(owner);

                    if (profile.isPresent()) {
                        trusted.add(profile.get().getName());
                    }
                }


                return PlaceholderResult.value(trusted.size() > 0 ? Component.literal(String.join(", ", trusted)) : wildnessText);
            }
        });

        Placeholders.register(ResourceLocation.fromNamespaceAndPath("goml", "claim_trusted_uuid"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) {
                return PlaceholderResult.invalid("No player!");
            }


            Component wildnessText = GetOffMyLawn.CONFIG.placeholderNoClaimTrusted.text();
            if (arg != null) {
                wildnessText = parser.parseText(arg, ParserContext.of());
            }

            var claims = ClaimUtils.getClaimsAt(ctx.player().level(), ctx.player().blockPosition()).collect(Collectors.toList());

            if (claims.size() == 0) {
                return PlaceholderResult.value(wildnessText);
            } else {
                var claim = claims.get(0);

                List<String> trusted = new ArrayList<>();
                for (UUID owner : claim.getValue().getTrusted()) {
                    var profile = ctx.server().getProfileCache().get(owner);

                    if (profile.isPresent()) {
                        trusted.add(profile.get().getId().toString());
                    }
                }


                return PlaceholderResult.value(trusted.size() > 0 ? Component.literal(String.join(", ", trusted)) : wildnessText);
            }
        });

        Placeholders.register(ResourceLocation.fromNamespaceAndPath("goml", "claim_info"), (ctx, arg) -> {
            if (!ctx.hasPlayer()) {
                return PlaceholderResult.invalid("No player!");
            }


            var wildnessText = GetOffMyLawn.CONFIG.placeholderNoClaimInfo.text();
            var canBuildText = GetOffMyLawn.CONFIG.placeholderClaimCanBuildInfo.node();
            var cantBuildText = GetOffMyLawn.CONFIG.placeholderClaimCantBuildInfo.node();

            if (arg != null) {
                String[] texts = arg.replace("\\:", "&bslsh\001;").split(":");

                if (texts.length > 0) {
                    wildnessText = parser.parseText(texts[0].replace("&bslsh;\001", ":"), ParserContext.of());
                }
                if (texts.length > 1) {
                    canBuildText = parser.parseNode(texts[1].replace("&bslsh;\001", ":"));
                }
                if (texts.length > 2) {
                    cantBuildText = parser.parseNode(texts[2].replace("&bslsh;\001", ":"));
                }
            }

            var claims = ClaimUtils.getClaimsAt(ctx.player().level(), ctx.player().blockPosition()).collect(Collectors.toList());


            if (claims.size() == 0) {
                return PlaceholderResult.value(wildnessText);
            } else {
                var claim = claims.get(0);

                List<String> owners = new ArrayList<>();
                List<String> ownersUuid = new ArrayList<>();

                for (UUID owner : claim.getValue().getOwners()) {
                    var profile = ctx.server().getProfileCache().get(owner);

                    if (profile.isPresent()) {
                        owners.add(profile.get().getName());
                        ownersUuid.add(profile.get().getId().toString());
                    }
                }
                List<String> trusted = new ArrayList<>();
                List<String> trustedUuid = new ArrayList<>();
                for (UUID owner : claim.getValue().getTrusted()) {
                    var profile = ctx.server().getProfileCache().get(owner);

                    if (profile.isPresent()) {
                        trusted.add(profile.get().getName());
                        trustedUuid.add(profile.get().getId().toString());
                    }
                }


                return PlaceholderResult.value(TagLikeParser.placeholderText(TagLikeParser.PLACEHOLDER_USER,
                        Map.of("owners", Component.literal(String.join(", ", owners)),
                                "owners_uuid", Component.literal(String.join(", ", ownersUuid)),
                                "trusted", Component.literal(String.join(", ", trusted)),
                                "trusted_uuid", Component.literal(String.join(", ", trustedUuid)),
                                "anchor", Component.literal(claim.getValue().getOrigin().toShortString()),
                                "status", draylar.goml.ui.ClaimMaintenanceGui.status(claim.getValue())
                        )::get).parseText(ClaimUtils.canModify(ctx.player().level(), ctx.player().blockPosition(), ctx.player()) ? canBuildText : cantBuildText, ParserContext.of())
                        .copy().append(Component.literal(" ")).append(draylar.goml.ui.ClaimMaintenanceGui.status(claim.getValue())));
            }
        });
    }
}
