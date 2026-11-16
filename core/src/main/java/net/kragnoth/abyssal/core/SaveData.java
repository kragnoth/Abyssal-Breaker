package net.kragnoth.abyssal.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything that persists between runs: scrip, Foundry ranks, records and the tarot hand. Serialised with Gson. */
public final class SaveData {
    public int v = 1;
    public long scrip;
    public Map<String, Integer> ranks = new HashMap<>();
    public long spent;
    public int bought;
    public long t;
    public List<Card> hand = new ArrayList<>();
    public Card pending;
    public Records rec = new Records();

    public static final class Records {
        public int runs, bestDepth, fission, maxCores, maxBanksRun, wins;
        /** Bit i set = level i cleared. */
        public int cleared;
        public long bestScore, totalScrip;
        public boolean everBought;
    }

    public static final class Card {
        public int n;
        public boolean rev;
        public Card() {}
        public Card(int n, boolean rev) { this.n = n; this.rev = rev; }
    }

    /** Fill in anything a hand-edited or older save left out. */
    public SaveData normalised() {
        if (ranks == null) ranks = new HashMap<>();
        if (hand == null) hand = new ArrayList<>();
        while (hand.size() > 3) hand.remove(hand.size() - 1);
        if (rec == null) rec = new Records();
        if (bought > 0 || spent > 0) rec.everBought = true;
        return this;
    }
}
