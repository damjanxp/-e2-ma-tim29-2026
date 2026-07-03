package com.example.slagalica.data.model;

import androidx.annotation.Nullable;

/**
 * POJO klasa koja pamti prva tri regiona po zvezdama poslednjeg zaključenog
 * mesečnog ciklusa. Koristi se za obeležavanje regiona na mapi (zlatno,
 * srebrno, bronzano) i za okvir avatara igrača tog regiona (vidi
 * {@code User#avatarFrameType}).
 *
 * <p>Firestore zahteva: public no-arg konstruktor, public getteri i setteri.</p>
 */
public class RegionCycleResult {

    @Nullable private String firstRegion;
    @Nullable private String secondRegion;
    @Nullable private String thirdRegion;
    private long concludedAt;

    /** Prazan konstruktor — obavezan za Firestore deserijalizaciju. */
    public RegionCycleResult() {
    }

    public RegionCycleResult(@Nullable String firstRegion, @Nullable String secondRegion,
                              @Nullable String thirdRegion, long concludedAt) {
        this.firstRegion = firstRegion;
        this.secondRegion = secondRegion;
        this.thirdRegion = thirdRegion;
        this.concludedAt = concludedAt;
    }

    @Nullable public String getFirstRegion() { return firstRegion; }
    public void setFirstRegion(@Nullable String firstRegion) { this.firstRegion = firstRegion; }

    @Nullable public String getSecondRegion() { return secondRegion; }
    public void setSecondRegion(@Nullable String secondRegion) { this.secondRegion = secondRegion; }

    @Nullable public String getThirdRegion() { return thirdRegion; }
    public void setThirdRegion(@Nullable String thirdRegion) { this.thirdRegion = thirdRegion; }

    public long getConcludedAt() { return concludedAt; }
    public void setConcludedAt(long concludedAt) { this.concludedAt = concludedAt; }
}
