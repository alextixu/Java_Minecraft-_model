package io.github.alextixu.mobarmory.web;

import java.net.URI;
import java.util.Objects;

/**
 * What a player is told about the web admin when they join (decision O6; web fixes B1-1 to B1-3). Plain Java, so the
 * rule is tested without the game; {@code forge/WebAdminLifecycle} turns it into a chat line.
 */
public sealed interface AdminGreeting {

    /**
     * The web admin is running. {@code shown} is the text in chat and never carries the token, because the game writes
     * chat text to the player's log; {@code open} is what a click opens, with the token when web.json sets one.
     */
    record Link(URI shown, URI open) implements AdminGreeting {
        public Link {
            Objects.requireNonNull(shown, "shown");
            Objects.requireNonNull(open, "open");
        }
    }

    /** The web admin did not start; game masters see why. */
    record Failed(String problem) implements AdminGreeting {
        public Failed {
            Objects.requireNonNull(problem, "problem");
        }
    }

    /** Nothing to say: the player is not a game master, or the web admin is off. */
    record Silent() implements AdminGreeting {
    }

    /**
     * @param gameMaster whether the player may use the web admin (permission level 2, like {@code /mobarmory reload})
     * @param running    the web admin, or null if it is not running
     * @param problem    why it did not start, or null
     */
    static AdminGreeting forPlayer(boolean gameMaster, AdminHttpServer running, String problem) {
        if (!gameMaster) {
            return new Silent();
        }
        if (running != null) {
            return new Link(running.uri(), running.linkWithToken());
        }
        return problem != null ? new Failed(problem) : new Silent();
    }
}
