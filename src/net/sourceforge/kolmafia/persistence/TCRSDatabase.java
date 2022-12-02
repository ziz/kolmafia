package net.sourceforge.kolmafia.persistence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.java.dev.spellcast.utilities.DataUtilities;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.AscensionClass;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLConstants.ConsumptionType;
import net.sourceforge.kolmafia.KoLmafia;
import net.sourceforge.kolmafia.Modifiers;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.RequestThread;
import net.sourceforge.kolmafia.StaticEntity;
import net.sourceforge.kolmafia.ZodiacSign;
import net.sourceforge.kolmafia.objectpool.Concoction;
import net.sourceforge.kolmafia.objectpool.ConcoctionPool;
import net.sourceforge.kolmafia.objectpool.EffectPool;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.persistence.ConsumablesDatabase.ConsumableQuality;
import net.sourceforge.kolmafia.request.CampgroundRequest;
import net.sourceforge.kolmafia.session.InventoryManager;
import net.sourceforge.kolmafia.utilities.FileUtilities;
import net.sourceforge.kolmafia.utilities.InputFieldUtilities;
import net.sourceforge.kolmafia.utilities.LogStream;
import net.sourceforge.kolmafia.utilities.PHPMTRandom;
import net.sourceforge.kolmafia.utilities.PHPRandom;
import net.sourceforge.kolmafia.utilities.StringUtilities;

public class TCRSDatabase {
  private TCRSDatabase() {}

  // Item attributes that vary by class/sign in a Two Random Crazy Summer run
  public static class TCRS {
    public final String name;
    public final int size;
    public final ConsumableQuality quality;
    public final String modifiers;

    TCRS(String name, int size, ConsumableQuality quality, String modifiers) {
      this.name = name;
      this.size = size;
      this.quality = quality;
      this.modifiers = modifiers;
    }
  }

  private static class TCRSDeriveRunnable implements Runnable {
    private int itemId;

    public TCRSDeriveRunnable(final int itemId) {
      this.itemId = itemId;
    }

    @Override
    public void run() {
      String text = DebugDatabase.itemDescriptionText(itemId, false);
      if (text == null) {
        return;
      }

      TCRS tcrs = deriveItem(text);

      if (tcrs == null) {
        return;
      }

      TCRSMap.put(itemId, tcrs);
    }
  }

  private static String currentClassSign; // Character class/Zodiac Sign

  // Sorted by itemId
  private static final Map<Integer, TCRS> TCRSMap = new TreeMap<Integer, TCRS>();
  private static final Map<Integer, TCRS> TCRSBoozeMap =
      new TreeMap<Integer, TCRS>(new CafeDatabase.InverseIntegerOrder());
  private static final Map<Integer, TCRS> TCRSFoodMap =
      new TreeMap<Integer, TCRS>(new CafeDatabase.InverseIntegerOrder());

  private static final List<Integer> TCRSEffectPool = new ArrayList<Integer>();

  static {
    TCRSDatabase.reset();
  }

  public static void reset() {
    currentClassSign = "";
    TCRSMap.clear();
    TCRSBoozeMap.clear();
    TCRSFoodMap.clear();
    TCRSEffectPool.clear();
    getEffectPool();
  }

  public static boolean hasData(int itemId) {
    return TCRSMap.containsKey(itemId);
  }

  public static String getTCRSName(int itemId) {
    TCRS tcrs = TCRSMap.get(itemId);
    return (tcrs == null) ? ItemDatabase.getDataName(itemId) : tcrs.name;
  }

  public static TCRS getData(int itemId) {
    return TCRSMap.get(itemId);
  }

  public static String filename() {
    return filename(KoLCharacter.getAscensionClass(), KoLCharacter.getSign(), "");
  }

  public static boolean validate(AscensionClass ascensionClass, ZodiacSign csign) {
    return (ascensionClass != null && ascensionClass.isStandard() && csign.isStandard());
  }

  public static String filename(AscensionClass ascensionClass, ZodiacSign sign, String suffix) {
    if (!validate(ascensionClass, sign)) {
      return "";
    }

    return "TCRS_"
        + StringUtilities.globalStringReplace(ascensionClass.getName(), " ", "_")
        + "_"
        + sign.getName()
        + suffix
        + ".txt";
  }

  public static boolean load(final boolean verbose) {
    if (!KoLCharacter.isCrazyRandomTwo()) {
      return false;
    }
    boolean retval = true;
    retval &= load(KoLCharacter.getAscensionClass(), KoLCharacter.getSign(), verbose);
    retval &= loadCafe(KoLCharacter.getAscensionClass(), KoLCharacter.getSign(), verbose);
    return retval;
  }

  public static boolean load(
      AscensionClass ascensionClass, ZodiacSign csign, final boolean verbose) {
    if (load(filename(ascensionClass, csign, ""), TCRSMap, verbose)) {
      currentClassSign = ascensionClass.getName() + "/" + csign;
      return true;
    }
    return false;
  }

  public static boolean loadCafe(
      AscensionClass ascensionClass, ZodiacSign csign, final boolean verbose) {
    boolean retval = true;
    retval &= load(filename(ascensionClass, csign, "_cafe_booze"), TCRSBoozeMap, verbose);
    retval &= load(filename(ascensionClass, csign, "_cafe_food"), TCRSFoodMap, verbose);
    return retval;
  }

  private static boolean load(String fileName, Map<Integer, TCRS> map, final boolean verbose) {
    map.clear();

    try (BufferedReader reader = FileUtilities.getReader(fileName)) {
      // No reader, no file
      if (reader == null) {
        if (verbose) {
          RequestLogger.printLine("Could not read file " + fileName);
        }
        return false;
      }

      String[] data;

      while ((data = FileUtilities.readData(reader)) != null) {
        if (data.length < 5) {
          continue;
        }
        int itemId = StringUtilities.parseInt(data[0]);
        String name = data[1];
        int size = StringUtilities.parseInt(data[2]);
        var quality = ConsumableQuality.find(data[3]);
        String modifiers = data[4];

        TCRS item = new TCRS(name, size, quality, modifiers);
        map.put(itemId, item);
      }
    } catch (IOException e) {
      StaticEntity.printStackTrace(e);
    }

    if (verbose) {
      RequestLogger.printLine("Read file " + fileName);
    }

    return true;
  }

  public static boolean save(final boolean verbose) {
    if (!KoLCharacter.isCrazyRandomTwo()) {
      return false;
    }
    boolean retval = true;
    retval &= save(KoLCharacter.getAscensionClass(), KoLCharacter.getSign(), verbose);
    retval &= saveCafe(KoLCharacter.getAscensionClass(), KoLCharacter.getSign(), verbose);
    return retval;
  }

  public static boolean save(
      AscensionClass ascensionClass, ZodiacSign csign, final boolean verbose) {
    return save(filename(ascensionClass, csign, ""), TCRSMap, verbose);
  }

  public static boolean saveCafe(
      AscensionClass ascensionClass, ZodiacSign csign, final boolean verbose) {
    boolean retval = true;
    retval &= save(filename(ascensionClass, csign, "_cafe_booze"), TCRSBoozeMap, verbose);
    retval &= save(filename(ascensionClass, csign, "_cafe_food"), TCRSFoodMap, verbose);
    return retval;
  }

  public static boolean saveCafeBooze(
      AscensionClass ascensionClass, ZodiacSign csign, final boolean verbose) {
    return save(filename(ascensionClass, csign, "_cafe_booze"), TCRSBoozeMap, verbose);
  }

  public static boolean saveCafeFood(
      AscensionClass ascensionClass, ZodiacSign csign, final boolean verbose) {
    return save(filename(ascensionClass, csign, "_cafe_food"), TCRSFoodMap, verbose);
  }

  private static boolean save(
      final String fileName, final Map<Integer, TCRS> map, final boolean verbose) {
    if (fileName == null) {
      return false;
    }

    PrintStream writer = LogStream.openStream(new File(KoLConstants.DATA_LOCATION, fileName), true);

    // No writer, no file
    if (writer == null) {
      if (verbose) {
        RequestLogger.printLine("Could not write file " + fileName);
      }
      return false;
    }

    for (Entry<Integer, TCRS> entry : map.entrySet()) {
      TCRS tcrs = entry.getValue();
      Integer itemId = entry.getKey();
      String name = tcrs.name;
      Integer size = tcrs.size;
      var quality = tcrs.quality;
      String modifiers = tcrs.modifiers;
      String line = itemId + "\t" + name + "\t" + size + "\t" + quality + "\t" + modifiers;
      writer.println(line);
    }

    writer.close();

    if (verbose) {
      RequestLogger.printLine("Wrote file " + fileName);
    }

    return true;
  }

  public static boolean derive(final boolean verbose) {
    if (!KoLCharacter.isCrazyRandomTwo()) {
      return false;
    }

    derive(KoLCharacter.getAscensionClass(), KoLCharacter.getSign(), verbose);
    deriveCafe(verbose);
    return true;
  }

  private static boolean derive(
      final AscensionClass ascensionClass, final ZodiacSign sign, final boolean verbose) {
    // If we don't currently have data for this class/sign, start fresh
    String classSign = ascensionClass.getName() + "/" + sign;
    if (!currentClassSign.equals(classSign)) {
      reset();
    }

    Set<Integer> keys = ItemDatabase.descriptionIdKeySet();

    if (verbose) {
      KoLmafia.updateDisplay("Deriving TCRS item adjustments for all real items...");
    }

    List<Runnable> actions = new ArrayList<>();

    for (Integer id : keys) {
      actions.add(new TCRSDeriveRunnable(id));
    }

    RequestThread.runInParallel(actions, verbose);

    currentClassSign = classSign;

    if (verbose) {
      KoLmafia.updateDisplay("Done!");
    }

    return true;
  }

  public static boolean derive(final int itemId) {
    // Don't do this if we already know the item
    if (TCRSMap.containsKey(itemId)) {
      return false;
    }

    TCRS tcrs = deriveItem(itemId);
    if (tcrs == null) {
      return false;
    }

    TCRSMap.put(itemId, tcrs);

    return true;
  }

  public static int update(final boolean verbose) {
    if (!KoLCharacter.isCrazyRandomTwo()) {
      return 0;
    }

    Set<Integer> keys = ItemDatabase.descriptionIdKeySet();

    if (verbose) {
      KoLmafia.updateDisplay("Updating TCRS item adjustments for real items...");
    }

    int count = 0;
    for (Integer id : keys) {
      // For a while, we stored the hewn moon-rune spoon
      // without modifiers.  If the data file we loaded has
      // that, force derive here to get the real modifiers.
      if (id == ItemPool.HEWN_MOON_RUNE_SPOON) {
        TCRS tcrs = TCRSMap.get(id);
        if (tcrs != null && "hewn moon-rune spoon".equals(tcrs.name)) {
          TCRSMap.remove(id);
        }
      }

      if (derive(id)) {
        count++;
      }
    }

    if (verbose) {
      KoLmafia.updateDisplay(count + " new items seen");
    }

    return count;
  }

  public static int updateCafeBooze(final boolean verbose) {
    if (!KoLCharacter.isCrazyRandomTwo()) {
      return 0;
    }

    if (verbose) {
      KoLmafia.updateDisplay("Updating TCRS item adjustments for cafe booze items...");
    }

    int count = 0;
    for (Integer id : CafeDatabase.cafeBoozeKeySet()) {
      if (deriveCafe(id, CafeDatabase.boozeDescId(id), TCRSBoozeMap)) {
        count++;
      }
    }

    if (verbose) {
      KoLmafia.updateDisplay(count + " new cafe boozes seen");
    }

    return count;
  }

  public static int updateCafeFood(final boolean verbose) {
    if (!KoLCharacter.isCrazyRandomTwo()) {
      return 0;
    }

    if (verbose) {
      KoLmafia.updateDisplay("Updating TCRS item adjustments for cafe food items...");
    }

    int count = 0;
    for (Integer id : CafeDatabase.cafeFoodKeySet()) {
      if (deriveCafe(id, CafeDatabase.foodDescId(id), TCRSFoodMap)) {
        count++;
      }
    }

    if (verbose) {
      KoLmafia.updateDisplay(count + " new cafe foods seen");
    }

    return count;
  }

  public static TCRS deriveItem(final int itemId) {
    // The "ring" is the path reward for completing a TCRS run.
    // Its enchantments are character-specific.
    if (itemId == ItemPool.RING) {
      return new TCRS("ring", 0, ConsumableQuality.NONE, "Single Equip");
    }

    // Read the Item Description
    String text = DebugDatabase.itemDescriptionText(itemId, false);
    if (text == null) {
      return null;
    }
    return deriveItem(text);
  }

  public static TCRS deriveAndSaveItem(final int itemId) {
    TCRS tcrs = deriveItem(itemId);
    if (tcrs != null) {
      TCRSMap.put(itemId, tcrs);
    }
    return tcrs;
  }

  public static TCRS deriveRing() {
    String text = DebugDatabase.itemDescriptionText(ItemPool.RING, false);
    return deriveItem(text);
  }

  public static TCRS deriveSpoon() {
    String text = DebugDatabase.itemDescriptionText(ItemPool.HEWN_MOON_RUNE_SPOON, false);
    return deriveItem(text);
  }

  public static void deriveApplyItem(final int id) {
    applyModifiers(id, deriveItem(DebugDatabase.itemDescriptionText(id, false)));
  }

  private static TCRS deriveItem(final String text) {
    // Parse the things that are changed in TCRS
    String name = DebugDatabase.parseName(text);
    int size = DebugDatabase.parseConsumableSize(text);
    var quality = DebugDatabase.parseQuality(text);
    ArrayList<String> unknown = new ArrayList<String>();
    String modifiers = DebugDatabase.parseItemEnchantments(text, unknown, ConsumptionType.UNKNOWN);

    // Create and return the TCRS object
    return new TCRS(name, size, quality, modifiers);
  }

  private static final List<String> COLOR_MODS =
      List.of(
          "red",
          "lime green",
          "blue",
          "gray",
          "maroon",
          "yellow",
          "olive",
          "cyan",
          "teal",
          "green",
          "fuchsia",
          "purple");

  private static final List<String> COSMETIC_MODS =
      List.of(
          "narrow",
          "huge",
          "skewed",
          "blinking",
          "upside-down",
          "mirror",
          "wobbly",
          "twirling",
          "pulsating",
          "jittery",
          "squat",
          "spinning",
          "tumbling",
          "shaking",
          "ghostly",
          "blurry",
          "bouncing");

  private static final List<String> POTION_MODS =
      List.of(
          "galvanized",
          "liquefied",
          "magnetized",
          "nitrogenated",
          "oxidized",
          "polarized",
          "polymerized",
          "quantum",
          "tarnished",
          "vacuum-sealed",
          "energized",
          "frozen",
          "diffused",
          "electrified",
          "concentrated",
          "colloidal",
          "activated",
          "aerosolized",
          "anodized",
          "alkaline",
          "ionized",
          "deionized",
          "denatured",
          "pickled",
          "cold-filtered",
          "boiled",
          "modified",
          "altered",
          "corrupted",
          "unsweetened",
          "improved",
          "adjusted",
          "enhanced",
          "moist",
          "dry",
          "chilled",
          "warmed",
          "ionized",
          "Vulcanized",
          "wet",
          "dry",
          "pressed",
          "flattened",
          "irradiated");

  private static final List<String> POTION_PREFIXES =
      List.of("double", "triple", "quadruple", "extra", "non", "super");

  private static final Set<String> ADJECTIVES =
      new HashSet<>(
          List.of(
              "Brimstone",
              "Spooky",
              "aerogel",
              "ancient",
              "antique",
              "bakelite",
              "big",
              "black",
              "blue",
              "candied",
              "cheap",
              "cold",
              "creepy",
              "cursed",
              "cute",
              "delicious",
              "dirty",
              "disintegrating",
              "dusty",
              "electric",
              "enchanted",
              "fancy",
              "fishy",
              "flaming",
              "floaty",
              "frozen",
              "fuchsia",
              "gabardine",
              "giant",
              "glowing",
              "gold",
              "golden",
              "green",
              "haunted",
              "large",
              "lavender",
              "leather",
              "little",
              "long",
              "lucky",
              "magical",
              "maroon",
              "metal",
              "miniature",
              "oily",
              "old",
              "orange",
              "oversized",
              "paisley",
              "paraffin",
              "polka-dot",
              "porcelain",
              "portable",
              "powdered",
              "primitive",
              "purple",
              "red",
              "silver",
              "sour",
              "spicy",
              "spooky",
              "stained",
              "sticky",
              "stinky",
              "strange",
              "striped",
              "stuffed",
              "tiny",
              "white",
              "wrought-iron",
              "yellow"));

  public static void getEffectPool() {
    EffectDatabase.entrySet().stream()
        .map(Map.Entry::getKey)
        // Effects must be marked as good
        .filter(id -> EffectDatabase.getQuality(id) == EffectDatabase.GOOD)
        // Effects must be hookah/wish-able
        .filter(id -> !EffectDatabase.hasAttribute(id, "nohookah"))
        // Some effects seem to be unavailable without any obvious reason, and so are tagged thusly
        .filter(id -> !EffectDatabase.hasAttribute(id, "notcrs"))
        // TCRS effects are limited to whatever was available at the time of the path (Tiki
        // Temerity)
        .filter(id -> id <= 2468)
        .forEachOrdered(TCRSEffectPool::add);
  }

  private static String removeAdjectives(final String name) {
    var words = Arrays.asList(name.split(" "));
    return String.join(" ", words.stream().filter(w -> !ADJECTIVES.contains(w)).toList());
  }

  private static String rollCosmetics(final PHPMTRandom mtRng, final PHPRandom rng, final int max) {
    // Determine cosmetic modifiers
    var cosmeticMods = new ArrayList<String>();

    //   Roll 1d6 on whether to add a color
    if (mtRng.nextInt(1, max) == 1) {
      cosmeticMods.add(mtRng.pickOne(COLOR_MODS));
    }

    //   Work out how many cosmetic modifiers to add
    var numCosmeticMods = 0;
    if (mtRng.nextInt(1, max) == 1) numCosmeticMods++;
    if (mtRng.nextInt(1, max) == 1) numCosmeticMods++;
    if (mtRng.nextInt(1, max) == 1) numCosmeticMods++;

    //   Pick and add cosmetic modifiers
    for (var i = 0; i < numCosmeticMods; i++) {
      cosmeticMods.add(mtRng.pickOne(COSMETIC_MODS));
    }

    if (cosmeticMods.size() > 0) {
      rng.shuffle(cosmeticMods);
    }

    Collections.reverse(cosmeticMods);

    return String.join(" ", cosmeticMods);
  }

  private static String rollConsumableEnchantment(final PHPMTRandom mtRng, final int itemId) {
    var hardcodedEffect = HARDCODED_EFFECT.contains(itemId);
    var hardcodedEffectDuration = HARDCODED_EFFECT_DURATION.contains(itemId);
    var roll = mtRng.nextInt(0, TCRSEffectPool.size());

    if (roll != TCRSEffectPool.size()) {
      var effectName = EffectPool.get(TCRSEffectPool.get(roll)).getDisambiguatedName();

      if (hardcodedEffect) {
        effectName = Modifiers.getStringModifier("Item", itemId, "Effect");
      }

      if (!effectName.isBlank()) {
        var duration = 5 * mtRng.nextInt(1, 10);

        if (hardcodedEffectDuration) {
          duration = (int) Modifiers.getNumericModifier("Item", itemId, "Effect Duration");
        }

        return "Effect: \"" + effectName + "\", Effect Duration: " + duration;
      }
    }

    return "";
  }

  public static TCRS guessPotion(
      final AscensionClass ascensionClass, final ZodiacSign sign, final AdventureResult item) {
    var seed = (50 * item.getItemId()) + (12345 * sign.getId()) + (100000 * ascensionClass.getId());
    var mtRng = new PHPMTRandom(seed);
    var rng = new PHPRandom(seed);

    var cosmeticsString = rollCosmetics(mtRng, rng, 6);

    // Determine potion modifiers
    var potionMods = new ArrayList<String>();

    //   Work out how many potion modifiers to add
    var numPotionMods = 1;
    if (mtRng.nextInt(1, 3) == 1) numPotionMods++;
    if (mtRng.nextInt(1, 3) == 1) numPotionMods++;

    //   Pick and add potion modifiers
    for (var i = 0; i < numPotionMods; i++) {
      potionMods.add(mtRng.pickOne(POTION_MODS));
    }

    // Pick effect (note that purposely pick a number that can overflow the pool by 1)
    var roll = mtRng.nextInt(0, TCRSEffectPool.size());

    var effectName =
        (roll == TCRSEffectPool.size())
            ?
            //   If we picked an overflow size, the item retains its original effect
            Modifiers.getStringModifier("Item", item.getDisambiguatedName(), "Effect")
            :
            //   Otherwise use the roll we got
            EffectPool.get(TCRSEffectPool.get(roll)).getDisambiguatedName();

    // @TODO what is going on here
    if (item.getItemId() == 3159 && roll == TCRSEffectPool.size()) {
      effectName = "";
    }

    // Pick duration of effect
    var duration = mtRng.nextInt(11, 69);

    // Pick potion mod prefixes
    var prefixedPotionMods = new ArrayList<String>();

    for (var mod : potionMods) {
      var prefixRoll = mtRng.nextInt(1, 40);
      if (prefixRoll <= POTION_PREFIXES.size()) {
        mod = POTION_PREFIXES.get(prefixRoll - 1) + "-" + mod;
      }

      // They get rendered in reverse
      prefixedPotionMods.add(0, mod);
    }

    var potionString = String.join(" ", prefixedPotionMods);

    var mods = "";
    if (!effectName.isBlank()) {
      mods = "Effect: \"" + effectName + "\", Effect Duration: " + duration;
    }

    var name =
        Stream.of(
                potionString,
                cosmeticsString,
                removeAdjectives(ItemDatabase.getItemName(item.getItemId())))
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.joining(" "));

    return new TCRS(name, 0, ConsumableQuality.NONE, mods);
  }

  private static ConsumableQuality determineFoodQuality(
      final int qualityRoll, final boolean beverage) {
    return switch (qualityRoll) {
      case 1 -> ConsumableQuality.CRAPPY;
      case 2 -> beverage ? ConsumableQuality.DECENT : ConsumableQuality.CRAPPY;
      case 3 -> ConsumableQuality.DECENT;
      case 4 -> beverage ? ConsumableQuality.GOOD : ConsumableQuality.DECENT;
      case 5 -> ConsumableQuality.GOOD;
      case 6 -> beverage ? ConsumableQuality.AWESOME : ConsumableQuality.GOOD;
      case 7 -> beverage ? ConsumableQuality.EPIC : ConsumableQuality.AWESOME;
      default -> null;
    };
  }

  private static ConsumableQuality determineBoozeQuality(final int qualityRoll) {
    return switch (qualityRoll) {
      case 1, 2 -> ConsumableQuality.DECENT;
      case 3, 4 -> ConsumableQuality.GOOD;
      case 5 -> ConsumableQuality.AWESOME;
      case 6, 7 -> ConsumableQuality.EPIC;
      default -> null;
    };
  }

  private static ConsumableQuality determineSpleenQuality(final int qualityRoll) {
    return switch (qualityRoll) {
      case 1 -> ConsumableQuality.CRAPPY;
      case 2, 3 -> ConsumableQuality.DECENT;
      case 4, 5 -> ConsumableQuality.GOOD;
      case 6 -> ConsumableQuality.AWESOME;
      case 7 -> ConsumableQuality.EPIC;
      default -> null;
    };
  }

  private static final List<List<String>> FOOD_SIZE_DESCRIPTORS =
      List.of(
          List.of("tiny", "bite-sized", "diet", "low-calorie"),
          List.of("small", "snack-sized", "half-sized", "miniature"),
          List.of(),
          List.of(),
          List.of("big", "thick", "super-sized", "jumbo"),
          List.of("massive", "gigantic", "huge", "immense"));

  private static final List<List<String>> BOOZE_SIZE_DESCRIPTORS =
      List.of(
          List.of("practically non-alcoholic"),
          List.of("weak", "watered-down"),
          List.of(),
          List.of(),
          List.of("strong", "spirit-forward", "fortified", "boozy", "distilled", "extra-dry"),
          List.of("irresponsibly strong", "high-proof", "triple-distilled"));

  private static final List<String> FOOD_BOOZE_ENCHANTMENT_DESCRIPTOR =
      List.of("special", "fancy", "enchanted");

  private static final Map<ConsumableQuality, List<String>> FOOD_QUALITY_DESCRIPTORS =
      Map.ofEntries(
          Map.entry(ConsumableQuality.CRAPPY, List.of("rotten", "spoiled", "moldy")),
          Map.entry(ConsumableQuality.DECENT, List.of("bland", "stale", "flavorless")),
          Map.entry(ConsumableQuality.GOOD, List.of("decent", "adequate", "normal")),
          Map.entry(ConsumableQuality.AWESOME, List.of("delicious", "tasty", "toothsome", "yummy")),
          Map.entry(ConsumableQuality.EPIC, List.of("")));

  private static final Map<ConsumableQuality, List<String>> BOOZE_QUALITY_DESCRIPTORS =
      Map.ofEntries(
          Map.entry(ConsumableQuality.CRAPPY, List.of("")),
          Map.entry(ConsumableQuality.DECENT, List.of("bad", "lousy", "mediocre")),
          Map.entry(ConsumableQuality.GOOD, List.of("acceptable", "drinkable", "tolerable")),
          Map.entry(ConsumableQuality.AWESOME, List.of("delicious", "smooth", "aged")),
          Map.entry(
              ConsumableQuality.EPIC, List.of("perfectly mixed", "artisanal", "hand-crafted")));

  private static TCRS guessFoodBooze(
      final AscensionClass ascensionClass,
      final ZodiacSign sign,
      final AdventureResult item,
      final boolean isFood) {
    var seed = (50 * item.getItemId()) + (12345 * sign.getId()) + (100000 * ascensionClass.getId());
    var mtRng = new PHPMTRandom(seed);
    var rng = new PHPRandom(seed);

    var beverage = ConsumablesDatabase.isBeverage(item.getItemId());

    var cosmeticsString = rollCosmetics(mtRng, rng, beverage ? 8 : 10);

    var qualityRoll = mtRng.nextInt(1, 7);
    var quality =
        isFood ? determineFoodQuality(qualityRoll, beverage) : determineBoozeQuality(qualityRoll);

    // Does it roll the size if a beverage?
    var size =
        beverage
            ? 1
            : switch (mtRng.nextInt(1, 10)) {
              case 1 -> 1;
              case 2, 3 -> 2;
              case 4, 5, 6 -> 3;
              case 7, 8 -> 4;
              case 9 -> 5;
              case 10 -> 5 + mtRng.nextInt(1, 5);
              default -> 0;
            };

    var adjectives = new ArrayList<String>();

    if (!beverage) {
      var sizeDescriptors =
          (isFood ? FOOD_SIZE_DESCRIPTORS : BOOZE_SIZE_DESCRIPTORS).get(Math.min(size - 1, 5));
      if (sizeDescriptors.size() > 0) {
        var sizeDescriptor = mtRng.pickOne(sizeDescriptors);
        adjectives.add(sizeDescriptor);
      }

      var qualityDescriptors =
          (isFood ? FOOD_QUALITY_DESCRIPTORS : BOOZE_QUALITY_DESCRIPTORS).get(quality);
      var qualityDescriptor =
          qualityDescriptors.size() > 1
              ? mtRng.pickOne(qualityDescriptors)
              : qualityDescriptors.get(0);
      adjectives.add(qualityDescriptor);
    }

    if (quality.getValue() * size >= 5) {
      mtRng.nextDouble();
    }

    var enchantmentDescriptor = "";
    if (mtRng.nextInt(1, 10) == 1) {
      enchantmentDescriptor = mtRng.pickOne(FOOD_BOOZE_ENCHANTMENT_DESCRIPTOR);
      adjectives.add(enchantmentDescriptor);
    }

    var mods =
        enchantmentDescriptor.equals("enchanted")
            ? rollConsumableEnchantment(mtRng, item.getItemId())
            : "";

    rng.shuffle(adjectives);

    Collections.reverse(adjectives);

    adjectives.add(cosmeticsString);
    adjectives.add(removeAdjectives(ItemDatabase.getItemName(item.getItemId())));

    var name =
        adjectives.stream().filter(Predicate.not(String::isBlank)).collect(Collectors.joining(" "));

    return new TCRS(name, size, quality, mods);
  }

  private static final List<String> SPLEEN_MODIFIERS =
      List.of(
          "boiled",
          "dried",
          "dehydrated",
          "diluted",
          "powdered",
          "mixed",
          "distilled",
          "altered",
          "modified",
          "twisted",
          "vaporized",
          "denatured",
          "compressed",
          "pickled");

  /** Items whose item types are ignored for TCRS */
  private static final Set<Integer> TCRS_GENERIC =
      Set.of(
          // Potions
          ItemPool.JAZZ_SOAP,
          ItemPool.CAN_OF_BINARRRCA,
          ItemPool.LOVE_POTION_XYZ,
          // Food
          ItemPool.LUCIFER,
          1555,
          5672,
          7091,
          8462,
          8899,
          // Booze
          5673);

  /** Items that are entirely unaffected by TCRS */
  private static final Set<Integer> TCRS_IMMUNE =
      Set.of(
          ItemPool.EXPERIMENTAL_CRIMBO_FOOD,
          ItemPool.EXPERIMENTAL_CRIMBO_BOOZE,
          ItemPool.EXPERIMENTAL_CRIMBO_SPLEEN,
          ItemPool.QUANTUM_TACO,
          ItemPool.SCHRODINGERS_THERMOS,
          ItemPool.SMORE,
          ItemPool.GLITCH_ITEM,
          ItemPool.DIABOLIC_PIZZA,
          ItemPool.VAMPIRE_VINTNER_WINE);

  /** Items that keep their effect despite rolling for a new one */
  private static final Set<Integer> HARDCODED_EFFECT =
      Set.of(
          ItemPool.WREATH_CRIMBO_COOKIE,
          ItemPool.BELL_CRIMBO_COOKIE,
          ItemPool.TREE_CRIMBO_COOKIE,
          ItemPool.BAT_CRIMBOWEEN_COOKIE,
          ItemPool.SKULL_CRIMBOWEEN_COOKIE,
          ItemPool.TOMBSTONE_CRIMBOWEEN_COOKIE,
          ItemPool.BEEFY_FISH_MEAT,
          ItemPool.GLISTENING_FISH_MEAT,
          ItemPool.BLOB_CRIMBCOOKIE,
          ItemPool.QUEEN_COOKIE,
          ItemPool.SUN_DRIED_TOFU,
          ItemPool.SOYBURGER_JUICE,
          ItemPool.CIRCULAR_CRIMBCOOKIE,
          ItemPool.TRIANGULAR_CRIMBCOOKIE,
          ItemPool.SQUARE_CRIMBCOOKIE,
          ItemPool.CHAOS_POPCORN,
          ItemPool.TEMPS_TEMPRANILLO,
          ItemPool.THYME_JELLY_DONUT);

  /** Items that keep their effect duration despite rolling for a new one */
  private static final Set<Integer> HARDCODED_EFFECT_DURATION =
      Set.of(
          ItemPool.WREATH_CRIMBO_COOKIE,
          ItemPool.BELL_CRIMBO_COOKIE,
          ItemPool.TREE_CRIMBO_COOKIE,
          ItemPool.BAT_CRIMBOWEEN_COOKIE,
          ItemPool.SKULL_CRIMBOWEEN_COOKIE,
          ItemPool.TOMBSTONE_CRIMBOWEEN_COOKIE,
          ItemPool.BEEFY_FISH_MEAT,
          ItemPool.GLISTENING_FISH_MEAT,
          ItemPool.BLOB_CRIMBCOOKIE,
          ItemPool.SUN_DRIED_TOFU,
          ItemPool.SOYBURGER_JUICE,
          ItemPool.CIRCULAR_CRIMBCOOKIE,
          ItemPool.TRIANGULAR_CRIMBCOOKIE,
          ItemPool.SQUARE_CRIMBCOOKIE,
          ItemPool.CHAOS_POPCORN,
          ItemPool.TEMPS_TEMPRANILLO,
          ItemPool.THYME_JELLY_DONUT);

  private static TCRS guessSpleen(
      final AscensionClass ascensionClass, final ZodiacSign sign, final AdventureResult item) {
    var seed = (50 * item.getItemId()) + (12345 * sign.getId()) + (100000 * ascensionClass.getId());
    var mtRng = new PHPMTRandom(seed);
    var rng = new PHPRandom(seed);

    var cosmeticsString = rollCosmetics(mtRng, rng, 4);

    var quality = determineSpleenQuality(mtRng.nextInt(1, 7));

    var adjective = mtRng.pickOne(SPLEEN_MODIFIERS);

    // Some unknown machinations here, only CDM can explain
    {
      if (quality == ConsumableQuality.CRAPPY) {
        if (mtRng.nextInt(1, 6) == 6) {
          mtRng.nextDouble();
        }
      } else {
        mtRng.nextDouble();
        mtRng.nextDouble();
      }

      mtRng.nextInt();
    }

    var mods = (mtRng.nextInt(1, 3) == 1) ? rollConsumableEnchantment(mtRng, item.getItemId()) : "";

    var name =
        Stream.of(
                adjective,
                cosmeticsString,
                removeAdjectives(ItemDatabase.getItemName(item.getItemId())))
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.joining(" "));

    return new TCRS(name, 1, quality, mods);
  }

  public static TCRS guessItem(
      final AscensionClass ascensionClass, final ZodiacSign sign, final int itemId) {
    var item = ItemPool.get(itemId);
    var type = EquipmentDatabase.getItemType(itemId);

    if (TCRS_GENERIC.contains(itemId) || TCRS_IMMUNE.contains(itemId)) {
      type = "other";
    }

    return switch (type) {
      case "potion", "avatar potion" -> guessPotion(ascensionClass, sign, item);
      case "food" -> guessFoodBooze(ascensionClass, sign, item, true);
      case "booze" -> guessFoodBooze(ascensionClass, sign, item, false);
      case "spleen item" -> guessSpleen(ascensionClass, sign, item);
      default -> null;
    };
  }

  private static boolean deriveCafe(final boolean verbose) {
    if (verbose) {
      KoLmafia.updateDisplay("Deriving TCRS item adjustments for all cafe booze items...");
    }

    for (Integer id : CafeDatabase.cafeBoozeKeySet()) {
      deriveCafe(id, CafeDatabase.boozeDescId(id), TCRSBoozeMap);
    }

    if (verbose) {
      KoLmafia.updateDisplay("Done!");
    }

    if (verbose) {
      KoLmafia.updateDisplay("Deriving TCRS item adjustments for all cafe food items...");
    }

    for (Integer id : CafeDatabase.cafeFoodKeySet()) {
      deriveCafe(id, CafeDatabase.foodDescId(id), TCRSFoodMap);
    }

    if (verbose) {
      KoLmafia.updateDisplay("Done!");
    }

    return true;
  }

  private static boolean deriveCafe(final int itemId, String descId, Map<Integer, TCRS> map) {
    // Don't do this if we already know the item
    if (map.containsKey(itemId)) {
      return false;
    }

    String text = DebugDatabase.cafeItemDescriptionText(descId);

    TCRS tcrs = deriveItem(text);
    if (tcrs == null) {
      return false;
    }

    map.put(itemId, tcrs);

    return true;
  }

  public static boolean applyModifiers() {
    // Remove food/booze/spleen/potion sources for effects
    StringBuilder buffer = new StringBuilder();
    for (Integer id : EffectDatabase.keys()) {
      String actions = EffectDatabase.getActions(id);
      if (actions == null || actions.startsWith("#")) {
        continue;
      }
      if (actions.contains("eat ")
          || actions.contains("drink ")
          || actions.contains("chew ")
          || actions.contains("use ")) {
        String[] split = actions.split(" *\\| *");
        buffer.setLength(0);
        for (String action : split) {
          if (action.equals("")
              || action.startsWith("eat ")
              || action.startsWith("drink ")
              || action.startsWith("chew ")
              || action.startsWith("use ")) {
            continue;
          }
          if (buffer.length() > 0) {
            buffer.append("|");
          }
          buffer.append(action);
        }
        EffectDatabase.setActions(id, buffer.length() == 0 ? null : buffer.toString());
      }
    }

    // Adjust non-cafe item data to have TCRS modifiers
    for (Entry<Integer, TCRS> entry : TCRSMap.entrySet()) {
      Integer id = entry.getKey();
      TCRS tcrs = entry.getValue();
      applyModifiers(id, tcrs);
    }

    // Do the same for cafe consumables
    for (Entry<Integer, TCRS> entry : TCRSBoozeMap.entrySet()) {
      Integer id = entry.getKey();
      TCRS tcrs = entry.getValue();
      String name = CafeDatabase.getCafeBoozeName(id.intValue());
      applyConsumableModifiers(ConsumptionType.DRINK, name, tcrs);
    }

    for (Entry<Integer, TCRS> entry : TCRSFoodMap.entrySet()) {
      Integer id = entry.getKey();
      TCRS tcrs = entry.getValue();
      String name = CafeDatabase.getCafeFoodName(id.intValue());
      applyConsumableModifiers(ConsumptionType.EAT, name, tcrs);
    }

    // Fix all the consumables whose adv yield varies by level
    ConsumablesDatabase.setLevelVariableConsumables();

    ConcoctionDatabase.refreshConcoctions();
    KoLCharacter.recalculateAdjustments();
    KoLCharacter.updateStatus();
    return true;
  }

  public static boolean applyModifiers(int itemId) {
    Integer id = itemId;
    return applyModifiers(id, TCRSMap.get(id));
  }

  private static int qualityMultiplier(ConsumableQuality quality) {
    return switch (quality) {
      case EPIC -> 5;
      case AWESOME -> 4;
      case GOOD -> 3;
      case DECENT -> 2;
      case CRAPPY -> 1;
      default -> 0;
    };
  }

  public static boolean applyModifiers(final Integer itemId, final TCRS tcrs) {
    // Adjust item data to have TCRS modifiers
    if (tcrs == null) {
      return false;
    }

    if (ItemDatabase.isFamiliarEquipment(itemId)) {
      return false;
    }

    if (IntStream.of(CampgroundRequest.campgroundItems).anyMatch(i -> i == itemId)) {
      return false;
    }

    String itemName = ItemDatabase.getItemDataName(itemId);
    if (itemName == null) {
      return false;
    }

    // Set modifiers
    Modifiers.updateItem(itemName, tcrs.modifiers);

    // *** Do this after modifiers are set so can log effect modifiers
    ConsumptionType usage = ItemDatabase.getConsumptionType(itemId);
    if (usage == ConsumptionType.EAT
        || usage == ConsumptionType.DRINK
        || usage == ConsumptionType.SPLEEN) {
      applyConsumableModifiers(usage, itemName, tcrs);
    }

    // Add as effect source, if appropriate
    String effectName = Modifiers.getStringModifier("Item", itemName, "Effect");
    if (effectName != null && !effectName.equals("")) {
      addEffectSource(itemName, usage, effectName);
    }

    // Whether or not there is an effect name, reset the concoction
    setEffectName(itemId, itemName);

    return true;
  }

  public static void setEffectName(final Integer itemId, String name) {
    Concoction c = ConcoctionPool.get(itemId, name);
    if (c != null) {
      c.setEffectName();
    }
  }

  private static void addEffectSource(
      final String itemName, final ConsumptionType usage, final String effectName) {
    int effectId = EffectDatabase.getEffectId(effectName);
    if (effectId == -1) {
      return;
    }
    String verb =
        switch (usage) {
          case EAT -> "eat ";
          case DRINK -> "drink ";
          case SPLEEN -> "chew ";
          default -> "use ";
        };
    String actions = EffectDatabase.getActions(effectId);
    boolean added = false;
    StringBuilder buffer = new StringBuilder();
    if (actions != null) {
      String either = verb + "either ";
      String[] split = actions.split(" *\\| *");
      for (String action : split) {
        if (action.isEmpty()) {
          continue;
        }
        if (buffer.length() > 0) {
          buffer.append("|");
        }
        if (added) {
          buffer.append(action);
          continue;
        }
        if (action.startsWith(either)) {
          buffer.append(action);
          buffer.append(", 1 ");
        } else if (action.startsWith(verb)) {
          buffer.append(StringUtilities.singleStringReplace(action, verb, either));
          buffer.append(", 1 ");
        } else {
          buffer.append(action);
          continue;
        }
        buffer.append(itemName);
        added = true;
      }
    }

    if (!added) {
      if (buffer.length() > 0) {
        buffer.append("|");
      }
      buffer.append(verb);
      buffer.append("1 ");
      buffer.append(itemName);
    }
    EffectDatabase.setActions(effectId, buffer.toString());
  }

  private static void applyConsumableModifiers(
      final ConsumptionType usage, final String itemName, final TCRS tcrs) {
    var consumable = ConsumablesDatabase.getConsumableByName(itemName);
    Integer lint = ConsumablesDatabase.getLevelReq(consumable);
    int level = lint == null ? 0 : lint;
    // Guess
    int adv = (usage == ConsumptionType.SPLEEN) ? 0 : (tcrs.size * qualityMultiplier(tcrs.quality));
    int mus = 0;
    int mys = 0;
    int mox = 0;

    var comment = new StringJoiner(", ").add("Unspaded");

    // Consumable attributes (like SAUCY, BEER, etc) are preserved
    ConsumablesDatabase.getAttributes(consumable).stream().map(Enum::name).forEach(comment::add);

    String effectName = Modifiers.getStringModifier("Item", itemName, "Effect");
    if (effectName != null && !effectName.isEmpty()) {
      int duration = (int) Modifiers.getNumericModifier("Item", itemName, "Effect Duration");
      String effectModifiers = Modifiers.getStringModifier("Effect", effectName, "Modifiers");
      comment.add(duration + " " + effectName + " (" + effectModifiers + ")");
    }

    ConsumablesDatabase.updateConsumable(
        itemName,
        tcrs.size,
        level,
        tcrs.quality,
        String.valueOf(adv),
        String.valueOf(mus),
        String.valueOf(mys),
        String.valueOf(mox),
        comment.toString());
  }

  public static void resetModifiers() {
    // Reset all the data structures that we altered in-place to
    // supper a particular TCRS class/sign to standard KoL values.

    // Nothing to reset if we didn't load TCRS data
    if (currentClassSign.equals("")) {
      return;
    }

    TCRSDatabase.reset();

    Modifiers.resetModifiers();
    EffectDatabase.reset();
    ConsumablesDatabase.reset();

    // Check items that vary per person
    InventoryManager.checkMods();

    deriveApplyItem(ItemPool.RING);

    ConcoctionDatabase.resetEffects();
    ConcoctionDatabase.refreshConcoctions();
    ConsumablesDatabase.setVariableConsumables();
    ConsumablesDatabase.calculateAllAverageAdventures();

    KoLCharacter.recalculateAdjustments();
    KoLCharacter.updateStatus();
  }

  // *** Primitives for checking presence of local files

  public static boolean localFileExists(
      AscensionClass ascensionClass, ZodiacSign sign, final boolean verbose) {
    boolean retval = false;
    retval |= localFileExists(filename(ascensionClass, sign, ""), verbose);
    return retval;
  }

  public static boolean localCafeFileExists(
      AscensionClass ascensionClass, ZodiacSign sign, final boolean verbose) {
    boolean retval = true;
    retval &= localFileExists(filename(ascensionClass, sign, "_cafe_booze"), verbose);
    retval &= localFileExists(filename(ascensionClass, sign, "_cafe_food"), verbose);
    return retval;
  }

  public static boolean anyLocalFileExists(
      AscensionClass ascensionClass, ZodiacSign sign, final boolean verbose) {
    boolean retval = false;
    retval |= localFileExists(filename(ascensionClass, sign, ""), verbose);
    retval |= localFileExists(filename(ascensionClass, sign, "_cafe_booze"), verbose);
    retval |= localFileExists(filename(ascensionClass, sign, "_cafe_food"), verbose);
    return retval;
  }

  private static boolean localFileExists(String localFilename, final boolean verbose) {
    if (localFilename == null) {
      return false;
    }
    File localFile = new File(KoLConstants.DATA_LOCATION, localFilename);
    return localFileExists(localFile, verbose);
  }

  private static boolean localFileExists(File localFile, final boolean verbose) {
    boolean exists = localFile.exists() && localFile.length() > 0;
    if (verbose) {
      RequestLogger.printLine(
          "Local file "
              + localFile.getName()
              + " "
              + (exists ? "already exists" : "does not exist")
              + ".");
    }
    return exists;
  }

  // *** support for fetching TCRS files from KoLmafia's SVN repository

  // Remote files we have fetched this session
  private static final Set<String> remoteFetched =
      new HashSet<String>(); // remote files fetched this session

  // *** Fetching files from the SVN repository, in two parts, since the
  // non-cafe code was released a week before the cafe code, and some
  // class/signs have only the non-cafe file

  public static boolean fetch(
      final AscensionClass ascensionClass, final ZodiacSign sign, final boolean verbose) {
    boolean retval = fetchRemoteFile(filename(ascensionClass, sign, ""), verbose);
    return retval;
  }

  public static boolean fetchCafe(
      final AscensionClass ascensionClass, final ZodiacSign sign, final boolean verbose) {
    boolean retval = true;
    retval &= fetchRemoteFile(filename(ascensionClass, sign, "_cafe_booze"), verbose);
    retval &= fetchRemoteFile(filename(ascensionClass, sign, "_cafe_food"), verbose);
    return retval;
  }

  // *** If we want to get all three files at once - and count it a
  // success as long as the non-cafe file is present -use these.
  // Not recommended.

  public static boolean fetchRemoteFiles(final boolean verbose) {
    return fetchRemoteFiles(KoLCharacter.getAscensionClass(), KoLCharacter.getSign(), verbose);
  }

  public static boolean fetchRemoteFiles(
      AscensionClass ascensionClass, ZodiacSign sign, final boolean verbose) {
    boolean retval = fetchRemoteFile(filename(ascensionClass, sign, ""), verbose);
    fetchRemoteFile(filename(ascensionClass, sign, "_cafe_booze"), verbose);
    fetchRemoteFile(filename(ascensionClass, sign, "_cafe_food"), verbose);
    return retval;
  }

  // *** Primitives for fetching a file from the SVN repository, overwriting existing file, if any.

  public static boolean fetchRemoteFile(String localFilename, final boolean verbose) {
    String remoteFileName =
        "https://raw.githubusercontent.com/kolmafia/kolmafia/main/data/TCRS/" + localFilename;
    if (remoteFetched.contains(remoteFileName)) {
      if (verbose) {
        RequestLogger.printLine(
            "Already fetched remote version of " + localFilename + " in this session.");
      }
      return true;
    }

    // Because we know we want a remote file the directory and override parameters will be ignored.
    File output = new File(KoLConstants.DATA_LOCATION, localFilename);

    try (BufferedReader remoteReader = DataUtilities.getReader("", remoteFileName, false);
        PrintWriter writer = new PrintWriter(new FileWriter(output))) {
      String aLine;
      while ((aLine = remoteReader.readLine()) != null) {
        // if the remote copy uses a different EOl than
        // the local OS then this will implicitly convert
        writer.println(aLine);
      }
      if (verbose) {
        RequestLogger.printLine(
            "Fetched remote version of " + localFilename + " from the repository.");
      }
    } catch (IOException exception) {
      // The reader and writer should be closed but since
      // that can throw an exception...
      RequestLogger.printLine("IO Exception for " + localFilename + ": " + exception.toString());
      return false;
    }

    if (output.length() <= 0) {
      // Do we care if we delete a file that is known to
      // exist and is empty?  No.
      if (verbose) {
        RequestLogger.printLine("File " + localFilename + " is empty. Deleting.");
      }
      output.delete();
      return false;
    }

    remoteFetched.add(remoteFileName);
    return true;
  }

  // *** support for loading up TCRS data appropriate to your current class/sign

  public static boolean loadTCRSData() {
    if (!KoLCharacter.isCrazyRandomTwo()) {
      return false;
    }

    return loadTCRSData(KoLCharacter.getAscensionClass(), KoLCharacter.getSign(), true);
  }

  private static boolean loadTCRSData(
      final AscensionClass ascensionClass, final ZodiacSign sign, final boolean verbose) {
    // If local TCRS data file is not present, fetch from repository
    if (!localFileExists(ascensionClass, sign, verbose)) {
      fetch(ascensionClass, sign, verbose);
    }

    boolean nonCafeLoaded = false;

    // If local TCRS data file is not present, offer to derive it
    if (!localFileExists(ascensionClass, sign, false)) {
      String message =
          "No TCRS data is available for "
              + ascensionClass.getName()
              + "/"
              + sign
              + ". Would you like to derive it? (This will take a long time, but you only have to do it once.)";
      if (InputFieldUtilities.confirm(message) && derive(ascensionClass, sign, verbose)) {
        save(ascensionClass, sign, verbose);
        nonCafeLoaded = true;
      } else {
        nonCafeLoaded = false;
      }

    }
    // Otherwise, load it
    else {
      nonCafeLoaded = load(ascensionClass, sign, verbose);
    }

    // Now do the same thing for cafe data.
    if (!localCafeFileExists(ascensionClass, sign, verbose)) {
      fetchCafe(ascensionClass, sign, verbose);
    }

    boolean cafeLoaded = false;

    // If local TCRS data file is not present, offer to derive it
    if (!localCafeFileExists(ascensionClass, sign, false)) {
      String message =
          "No TCRS cafe data is available for "
              + ascensionClass.getName()
              + "/"
              + sign
              + ". Would you like to derive it? (This will not take long, and you only have to do it once.)";
      if (InputFieldUtilities.confirm(message) && deriveCafe(verbose)) {

        saveCafe(ascensionClass, sign, verbose);
        cafeLoaded = true;
      } else {
        cafeLoaded = false;
      }

    }
    // Otherwise, load it
    else {
      cafeLoaded = loadCafe(ascensionClass, sign, verbose);
    }

    // If we loaded data files, update them.

    if (nonCafeLoaded) {
      if (update(verbose) > 0) {
        save(ascensionClass, sign, verbose);
      }
    }

    if (cafeLoaded) {
      if (updateCafeBooze(verbose) > 0) {
        saveCafeBooze(ascensionClass, sign, verbose);
      }
      if (updateCafeFood(verbose) > 0) {
        saveCafeFood(ascensionClass, sign, verbose);
      }
    }

    if (nonCafeLoaded || cafeLoaded) {
      applyModifiers();
      deriveApplyItem(ItemPool.RING);
    }

    return true;
  }
}
