package draylar.goml.cca;

import com.jamieswhiteshirt.rtree3i.RTreeMap;
import org.ladysnake.cca.api.v3.component.ComponentV3;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimBox;

public interface ClaimComponent extends ComponentV3 {
    RTreeMap<ClaimBox, Claim> getClaims();
    void add(Claim info);
    void remove(Claim info);
    void queueDrops(net.minecraft.core.BlockPos origin, java.util.List<net.minecraft.world.item.ItemStack> stacks);
    void tickPendingDrops();
}
