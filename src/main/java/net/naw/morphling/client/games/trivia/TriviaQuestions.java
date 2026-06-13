package net.naw.morphling.client.games.trivia;

import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Question bank for Morph Trivia.

 * Each entry is a { EntityType, clue string } pair.
 * Clues use \n for line breaks (max 2 lines recommended).
 * Questions are pulled randomly by MorphTriviaScreen — no repeats within a game.

 * Guidelines for writing good clues:
 *  - Never mention the mob's name directly
 *  - Focus on real Minecraft knowledge: spawning, drops, behavior, lore, weaknesses
 *  - No keybind or Morphling-specific hints — this is real trivia
 *  - Mix easy and hard questions
 *  - Keep it to 1-2 lines, readable at a glance
 */
public class TriviaQuestions {

    public record Question(EntityType<?> answer, String clue) {}

    public static List<Question> getAll() {
        List<Question> q = new ArrayList<>();

        // ── CHICKEN ──────────────────────────────────────────────────────────
        // Easy to medium — very recognizable mob
        q.add(new Question(EntityType.CHICKEN, "This mob drops feathers\nand raw meat when killed."));
        q.add(new Question(EntityType.CHICKEN, "This is the only mob that\nlays eggs passively over time."));
        q.add(new Question(EntityType.CHICKEN, "Baby zombies sometimes ride\nthis mob as a jockey."));
        q.add(new Question(EntityType.CHICKEN, "This mob takes no fall damage\ndue to its natural flutter."));
        q.add(new Question(EntityType.CHICKEN, "Throwing its dropped item\nhas a chance to spawn a baby."));
        q.add(new Question(EntityType.CHICKEN, "This mob spawns naturally\nin most grassy biomes."));
        q.add(new Question(EntityType.CHICKEN, "It drops 0-2 feathers\nand 1 raw food item on death."));
        q.add(new Question(EntityType.CHICKEN, "The only farm animal that\nproduces a throwable item."));

        // ── COW ──────────────────────────────────────────────────────────────
        // Easy — very well known
        q.add(new Question(EntityType.COW, "This mob can be milked\nwith an empty bucket."));
        q.add(new Question(EntityType.COW, "It drops leather and raw beef\nbut can also provide a liquid."));
        q.add(new Question(EntityType.COW, "Wheat is used to breed\nthis common farm mob."));
        q.add(new Question(EntityType.COW, "This mob provides the only\ndrinkable liquid in vanilla Minecraft."));
        q.add(new Question(EntityType.COW, "It drops up to 3 leather\nand up to 3 raw food on death."));
        q.add(new Question(EntityType.COW, "This passive mob spawns\nin plains, savannas, and forests."));
        q.add(new Question(EntityType.COW, "The Mooshroom is a variant\nof this mob found in mushroom biomes."));
        q.add(new Question(EntityType.COW, "Buckets are needed to harvest\nthis mob's renewable resource."));

        // ── PIG ──────────────────────────────────────────────────────────────
        // Easy to medium
        q.add(new Question(EntityType.PIG, "This mob can be ridden\nusing a saddle and a carrot on a stick."));
        q.add(new Question(EntityType.PIG, "Lightning strikes transform\nthis mob into a hostile variant."));
        q.add(new Question(EntityType.PIG, "It drops porkchops and\ncan be steered with the right food item."));
        q.add(new Question(EntityType.PIG, "A lightning bolt turns this\npink mob into a Zombified version."));
        q.add(new Question(EntityType.PIG, "Carrots, beetroots, and potatoes\nare used to breed this mob."));
        q.add(new Question(EntityType.PIG, "The only rideable passive mob\nthat requires a food item to steer."));
        q.add(new Question(EntityType.PIG, "This mob drops raw porkchop\nand has a zombie variant from lightning."));
        q.add(new Question(EntityType.PIG, "Saddle up, attach a carrot,\nand steer this mob anywhere."));

        // ── SHEEP ────────────────────────────────────────────────────────────
        // Easy to medium
        q.add(new Question(EntityType.SHEEP, "Shearing this mob gives wool\nwithout killing it."));
        q.add(new Question(EntityType.SHEEP, "This mob regrows its wool\nby eating grass blocks."));
        q.add(new Question(EntityType.SHEEP, "It can naturally spawn in\n16 different wool colors."));
        q.add(new Question(EntityType.SHEEP, "Named 'jeb_' this mob\ncycles through all wool colors."));
        q.add(new Question(EntityType.SHEEP, "The rarest natural spawn color\nfor this mob is pink."));
        q.add(new Question(EntityType.SHEEP, "Shears give more drops\nthan killing this mob."));
        q.add(new Question(EntityType.SHEEP, "This mob drops mutton\nand wool when killed."));
        q.add(new Question(EntityType.SHEEP, "Wheat breeds this mob.\nGrass regrows its coat."));

        // ── CAT ──────────────────────────────────────────────────────────────
        // Medium — specific behavior knowledge needed
        q.add(new Question(EntityType.CAT, "Creepers and phantoms\nactively avoid this mob."));
        q.add(new Question(EntityType.CAT, "This mob spawns in villages\nand swamp huts."));
        q.add(new Question(EntityType.CAT, "Taming this mob requires\nraw fish."));
        q.add(new Question(EntityType.CAT, "Phantoms will not attack a player\nif this mob is nearby."));
        q.add(new Question(EntityType.CAT, "This mob sometimes brings\ngifts to players when they sleep."));
        q.add(new Question(EntityType.CAT, "There are 11 different\ntexture variants of this mob."));
        q.add(new Question(EntityType.CAT, "Witch huts always spawn\none black version of this mob."));
        q.add(new Question(EntityType.CAT, "Creepers run away from\nthis domesticated mob."));

        // ── WOLF ─────────────────────────────────────────────────────────────
        // Easy to medium
        q.add(new Question(EntityType.WOLF, "Bones are used to tame\nthis mob in the wild."));
        q.add(new Question(EntityType.WOLF, "When tamed, this mob\nfights alongside its owner."));
        q.add(new Question(EntityType.WOLF, "This mob's eyes turn red\nwhen it becomes hostile."));
        q.add(new Question(EntityType.WOLF, "Untamed, it is neutral.\nTamed, it is loyal."));
        q.add(new Question(EntityType.WOLF, "This mob shakes itself dry\nafter being in water."));
        q.add(new Question(EntityType.WOLF, "It spawns in forests, taigas,\nand snowy biomes."));
        q.add(new Question(EntityType.WOLF, "Skeletons flee from this mob\neven in the wild."));
        q.add(new Question(EntityType.WOLF, "Any meat can heal this mob\nonce it is tamed."));

        // ── PARROT ───────────────────────────────────────────────────────────
        // Medium — some specific facts
        q.add(new Question(EntityType.PARROT, "This mob can be tamed\nwith any type of seed."));
        q.add(new Question(EntityType.PARROT, "Feeding this mob cookies\nis instantly fatal to it."));
        q.add(new Question(EntityType.PARROT, "This mob dances automatically\nwhen near a jukebox."));
        q.add(new Question(EntityType.PARROT, "It mimics the sounds of\nnearby hostile mobs as a warning."));
        q.add(new Question(EntityType.PARROT, "This mob only spawns\nin jungle biomes."));
        q.add(new Question(EntityType.PARROT, "There are 5 color variants\nof this tropical mob."));
        q.add(new Question(EntityType.PARROT, "Tamed, this mob sits on\nyour shoulder when you walk near it."));
        q.add(new Question(EntityType.PARROT, "Cookies are poisonous to this mob.\nSeeds are how you tame it."));

        // ── ZOMBIE ───────────────────────────────────────────────────────────
        // Easy to medium
        q.add(new Question(EntityType.ZOMBIE, "This mob can pick up\nand wear armor it finds."));
        q.add(new Question(EntityType.ZOMBIE, "Drowning transforms this mob\ninto a different underwater variant."));
        q.add(new Question(EntityType.ZOMBIE, "This mob burns in sunlight\nunless wearing a helmet."));
        q.add(new Question(EntityType.ZOMBIE, "Villagers killed by this mob\ncan turn into one of its kind."));
        q.add(new Question(EntityType.ZOMBIE, "Baby versions of this mob\nare faster than the adult."));
        q.add(new Question(EntityType.ZOMBIE, "This mob can call nearby\nfriends when attacked."));
        q.add(new Question(EntityType.ZOMBIE, "Splash potions of weakness\nand golden apples can cure its villager form."));
        q.add(new Question(EntityType.ZOMBIE, "It can break down wooden doors\non Hard difficulty in vanilla."));

        // ── SKELETON ─────────────────────────────────────────────────────────
        // Easy to medium
        q.add(new Question(EntityType.SKELETON, "This mob burns in sunlight\nand retreats to shade."));
        q.add(new Question(EntityType.SKELETON, "Wolves are actively avoided\nby this ranged mob."));
        q.add(new Question(EntityType.SKELETON, "It drops bones and arrows\nwhen killed."));
        q.add(new Question(EntityType.SKELETON, "This mob strays and wanders\nbut always keeps its distance to shoot."));
        q.add(new Question(EntityType.SKELETON, "A Stray is a cold-biome variant\nof this mob that shoots slowness arrows."));
        q.add(new Question(EntityType.SKELETON, "Rare chance to drop its bow\nwhich may be enchanted."));
        q.add(new Question(EntityType.SKELETON, "This mob can sometimes spawn\nriding a spider as a jockey."));
        q.add(new Question(EntityType.SKELETON, "It avoids wolves and flees\ninto shade when the sun rises."));

        // ── CREEPER ──────────────────────────────────────────────────────────
        // Easy — iconic mob
        q.add(new Question(EntityType.CREEPER, "This mob was created\nby a coding accident."));
        q.add(new Question(EntityType.CREEPER, "Cats scare this mob\ninto running away."));
        q.add(new Question(EntityType.CREEPER, "Killed by a skeleton's arrow,\nthis mob drops a music disc."));
        q.add(new Question(EntityType.CREEPER, "Lightning turns this mob\ninto a supercharged version."));
        q.add(new Question(EntityType.CREEPER, "This mob drops gunpowder\nand sometimes a disc."));
        q.add(new Question(EntityType.CREEPER, "It makes no sound before\nexploding — unlike ghasts."));
        q.add(new Question(EntityType.CREEPER, "Shears can be used to\nget a unique head from this mob."));
        q.add(new Question(EntityType.CREEPER, "The original design was meant\nto be a pig but the model was rotated."));

        // ── ENDERMAN ─────────────────────────────────────────────────────────
        // Medium to hard
        q.add(new Question(EntityType.ENDERMAN, "Looking directly at this mob\nmakes it hostile."));
        q.add(new Question(EntityType.ENDERMAN, "Water damages this mob\non contact."));
        q.add(new Question(EntityType.ENDERMAN, "This mob can pick up\nand move certain blocks."));
        q.add(new Question(EntityType.ENDERMAN, "Wearing a pumpkin on your head\nprevents this mob from aggroing."));
        q.add(new Question(EntityType.ENDERMAN, "This mob drops ender pearls\nwhich allow instant teleportation."));
        q.add(new Question(EntityType.ENDERMAN, "It teleports away\nwhen hit with a projectile."));
        q.add(new Question(EntityType.ENDERMAN, "This mob is 3 blocks tall —\nthe tallest common hostile mob."));
        q.add(new Question(EntityType.ENDERMAN, "Eye contact triggers aggression.\nPumpkin heads are the countermeasure."));

        // ── IRON GOLEM ───────────────────────────────────────────────────────
        // Medium
        q.add(new Question(EntityType.IRON_GOLEM, "This mob is built\nusing 4 iron blocks and a carved pumpkin."));
        q.add(new Question(EntityType.IRON_GOLEM, "It patrols villages and\nprotects villagers from threats."));
        q.add(new Question(EntityType.IRON_GOLEM, "This mob drops iron ingots\nand poppies when it dies."));
        q.add(new Question(EntityType.IRON_GOLEM, "Cracking on its body\nindicates how much health it has lost."));
        q.add(new Question(EntityType.IRON_GOLEM, "It can be healed using\niron ingots."));
        q.add(new Question(EntityType.IRON_GOLEM, "This mob sometimes offers\na poppy to baby villagers."));
        q.add(new Question(EntityType.IRON_GOLEM, "Player-built versions are neutral.\nNaturally spawned ones protect the village."));
        q.add(new Question(EntityType.IRON_GOLEM, "It has one of the highest\nhealth pools of any non-boss mob."));

        // ── DOLPHIN ──────────────────────────────────────────────────────────
        // Medium — less common knowledge
        q.add(new Question(EntityType.DOLPHIN, "This mob leads players\nto nearby ocean ruins or shipwrecks."));
        q.add(new Question(EntityType.DOLPHIN, "Feeding this mob raw fish\ntriggers its treasure-finding behavior."));
        q.add(new Question(EntityType.DOLPHIN, "Swimming near this mob\ngives the player a speed buff."));
        q.add(new Question(EntityType.DOLPHIN, "This mob cannot survive\nout of water for long."));
        q.add(new Question(EntityType.DOLPHIN, "It's neutral — it will attack\nif provoked by a player."));
        q.add(new Question(EntityType.DOLPHIN, "This mob needs both water\nand air to survive — it drowns if stuck."));
        q.add(new Question(EntityType.DOLPHIN, "The speed boost this mob gives nearby\nswimmers is called Dolphin's Grace."));
        q.add(new Question(EntityType.DOLPHIN, "Feeding it cod or salmon\nleads you to hidden underwater structures."));

        // ── HORSE ────────────────────────────────────────────────────────────
        // Easy to medium
        q.add(new Question(EntityType.HORSE, "This mob requires a saddle\nto be ridden and controlled."));
        q.add(new Question(EntityType.HORSE, "It can be equipped with\nhorse armor for protection."));
        q.add(new Question(EntityType.HORSE, "Taming this mob requires\nrepeated mounting until hearts appear."));
        q.add(new Question(EntityType.HORSE, "Speed, jump height, and health\nare all randomly determined at spawn."));
        q.add(new Question(EntityType.HORSE, "This mob spawns in herds\nin plains and savanna biomes."));
        q.add(new Question(EntityType.HORSE, "Golden apples and golden carrots\nare used to breed this mob."));
        q.add(new Question(EntityType.HORSE, "There are 35 coat and marking\ncombinations for this mob."));
        q.add(new Question(EntityType.HORSE, "The fastest movement speed\nfor this mob is about 14 blocks per second."));

        // ── VILLAGER ─────────────────────────────────────────────────────────
        // Medium to hard
        q.add(new Question(EntityType.VILLAGER, "This mob restocks trades\nby working at its job site block."));
        q.add(new Question(EntityType.VILLAGER, "Curing a zombie version\ngives permanent trade discounts."));
        q.add(new Question(EntityType.VILLAGER, "This mob runs indoors\nat night or during raids."));
        q.add(new Question(EntityType.VILLAGER, "There are 13 different\nprofessions for this mob."));
        q.add(new Question(EntityType.VILLAGER, "A Nitwit variant of this mob\nhas no profession and cannot trade."));
        q.add(new Question(EntityType.VILLAGER, "Gossip spreads between these mobs,\naffecting player reputation."));
        q.add(new Question(EntityType.VILLAGER, "Lightning near this mob\nturns it into a Witch."));
        q.add(new Question(EntityType.VILLAGER, "The Librarian profession\nfor this mob can trade enchanted books."));

        // ── SPIDER ───────────────────────────────────────────────────────────
        // Easy to medium
        q.add(new Question(EntityType.SPIDER, "This mob is neutral in daylight\nand hostile in darkness."));
        q.add(new Question(EntityType.SPIDER, "It can climb vertical surfaces\nincluding walls and ceilings."));
        q.add(new Question(EntityType.SPIDER, "This mob drops string and\nspider eyes when killed."));
        q.add(new Question(EntityType.SPIDER, "A skeleton riding this mob\nis called a Spider Jockey."));
        q.add(new Question(EntityType.SPIDER, "Cave Spider is a smaller,\nmore dangerous variant of this mob."));
        q.add(new Question(EntityType.SPIDER, "This mob can spawn with\npotion effects applied to it."));
        q.add(new Question(EntityType.SPIDER, "String dropped by this mob\nis used to craft bows and wool."));
        q.add(new Question(EntityType.SPIDER, "It has 8 eyes and can see\nthrough any invisibility effect."));

        // ── SLIME ────────────────────────────────────────────────────────────
        // Medium — specific spawn knowledge
        q.add(new Question(EntityType.SLIME, "This mob only spawns\nin slime chunks or swamp biomes."));
        q.add(new Question(EntityType.SLIME, "When killed, it splits into\nup to 4 smaller versions of itself."));
        q.add(new Question(EntityType.SLIME, "The smallest size of this mob\nis harmless and cannot deal damage."));
        q.add(new Question(EntityType.SLIME, "This mob drops slimeballs\nused to craft sticky pistons and leads."));
        q.add(new Question(EntityType.SLIME, "In swamps, this mob spawns\nmore frequently during a full moon."));
        q.add(new Question(EntityType.SLIME, "Magma Cube is the Nether\nequivalent of this overworld mob."));
        q.add(new Question(EntityType.SLIME, "Slimeballs from this mob\nare used to craft slime blocks."));
        q.add(new Question(EntityType.SLIME, "This mob can only spawn\nbelow Y=40 in specific chunk types."));

        // ── BEE ──────────────────────────────────────────────────────────────
        // Medium to hard — newer mob with specific mechanics
        q.add(new Question(EntityType.BEE, "After stinging a player,\nthis mob dies within a minute."));
        q.add(new Question(EntityType.BEE, "This mob pollinates flowers\nand brings nectar back to its hive."));
        q.add(new Question(EntityType.BEE, "A hive or nest filled by this mob\ncan be harvested for honeycomb."));
        q.add(new Question(EntityType.BEE, "This mob sleeps inside\nits hive at night or during rain."));
        q.add(new Question(EntityType.BEE, "Campfire smoke below a nest\ncalms this mob during harvesting."));
        q.add(new Question(EntityType.BEE, "When hostile, nearby members\nof this mob's colony join the attack."));
        q.add(new Question(EntityType.BEE, "This mob was added in\nthe Java 1.15 Buzzy Bees update."));
        q.add(new Question(EntityType.BEE, "Flowers are used to breed this mob\nand it follows players holding them."));
        q.add(new Question(EntityType.BEE, "This mob's sting inflicts\npoison for 10 seconds on Normal difficulty."));

        // Fox ──────────────────────────────────────────────────────────────
        q.add(new Question(EntityType.FOX, "This mob stalks and pounces\non chickens and rabbits."));
        q.add(new Question(EntityType.FOX, "It comes in two variants —\nred and snow white."));
        q.add(new Question(EntityType.FOX, "This mob sleeps during\nthe day and hunts at night."));
        q.add(new Question(EntityType.FOX, "It can pick up and carry\nitems in its mouth."));
        q.add(new Question(EntityType.FOX, "Sweet berries and glow berries\nare this mob's favorite food."));
        q.add(new Question(EntityType.FOX, "This mob spawns in taiga\nand snowy biomes."));
        q.add(new Question(EntityType.FOX, "It will defend players\nit has learned to trust."));
        q.add(new Question(EntityType.FOX, "This mob does a crouch-and-leap\nto catch its prey."));

        // Rabbit ──────────────────────────────────────────────────────────────
        q.add(new Question(EntityType.RABBIT, "This mob hops around and\nflees from players and wolves."));
        q.add(new Question(EntityType.RABBIT, "It comes in 7 variants including\na rare killer version."));
        q.add(new Question(EntityType.RABBIT, "Sweet berries and carrots\nare this mob's favorite food."));
        q.add(new Question(EntityType.RABBIT, "This mob raids gardens\nand eats fully grown carrots."));
        q.add(new Question(EntityType.RABBIT, "The rare evil version of this\nmob is named after Monty Python."));
        q.add(new Question(EntityType.RABBIT, "This mob spawns in snowy\nbiomes as a white variant."));
        q.add(new Question(EntityType.RABBIT, "Naming this mob 'Toast' gives\nit a special memorial skin."));
        q.add(new Question(EntityType.RABBIT, "This mob has only 1.5 hearts\nmaking it very fragile."));

        // Axolotl ──────────────────────────────────────────────────────────────
        q.add(new Question(EntityType.AXOLOTL, "This mob lives underwater and\ncan walk on land briefly."));
        q.add(new Question(EntityType.AXOLOTL, "It plays dead when hurt\nto confuse attackers."));
        q.add(new Question(EntityType.AXOLOTL, "This mob gives nearby players\nRegeneration after killing a mob."));
        q.add(new Question(EntityType.AXOLOTL, "It comes in 5 colors including\na rare blue variant."));
        q.add(new Question(EntityType.AXOLOTL, "This mob can be caught\nin a water bucket."));
        q.add(new Question(EntityType.AXOLOTL, "It takes damage when left\nout of water too long."));
        q.add(new Question(EntityType.AXOLOTL, "This mob targets fish and\ndrowned underwater."));
        q.add(new Question(EntityType.AXOLOTL, "Its rare variant has only\na 1 in 1200 spawn chance."));

        // Frog
        q.add(new Question(EntityType.FROG, "This mob eats small slimes\nand magma cubes with its tongue."));
        q.add(new Question(EntityType.FROG, "It comes in 3 variants:\nTemperate, Warm, and Cold."));
        q.add(new Question(EntityType.FROG, "This mob drops a froglight block\nwhen it eats a magma cube."));
        q.add(new Question(EntityType.FROG, "It lays eggs in water\ncalled frogspawn."));
        q.add(new Question(EntityType.FROG, "This mob is the only one\nthat produces froglight blocks."));

        // Polar Bear
        q.add(new Question(EntityType.POLAR_BEAR, "This mob is passive unless\nyou approach its cubs."));
        q.add(new Question(EntityType.POLAR_BEAR, "It rears up on its hind legs\nto warn attackers."));
        q.add(new Question(EntityType.POLAR_BEAR, "This mob attacks foxes\nto protect its cubs."));
        q.add(new Question(EntityType.POLAR_BEAR, "It spawns exclusively\nin snowy biomes."));


        // Panda
        q.add(new Question(EntityType.PANDA, "This mob comes in 7 gene variants\nincluding lazy, playful, and aggressive."));
        q.add(new Question(EntityType.PANDA, "It only eats bamboo\nand bamboo shoots."));
        q.add(new Question(EntityType.PANDA, "Baby pandas occasionally\nsneeze, startling nearby adults."));
        q.add(new Question(EntityType.PANDA, "The playful variant likes\nto roll around on the ground."));
        q.add(new Question(EntityType.PANDA, "Two pandas need nearby bamboo\nto be willing to breed."));

        return q;

    }
}