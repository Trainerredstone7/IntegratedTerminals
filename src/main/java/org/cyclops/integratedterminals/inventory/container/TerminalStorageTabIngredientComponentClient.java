package org.cyclops.integratedterminals.inventory.container;

import com.google.common.collect.Lists;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import net.minecraft.item.ItemStack;
import org.cyclops.commoncapabilities.api.ingredient.IIngredientMatcher;
import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.cyclopscore.ingredient.collection.IIngredientCollapsedCollectionMutable;
import org.cyclops.cyclopscore.ingredient.collection.IIngredientListMutable;
import org.cyclops.cyclopscore.ingredient.collection.IngredientArrayList;
import org.cyclops.cyclopscore.ingredient.collection.IngredientCollectionPrototypeMap;
import org.cyclops.integrateddynamics.api.ingredient.IIngredientComponentStorageObservable;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetwork;
import org.cyclops.integratedterminals.api.ingredient.IIngredientComponentViewHandler;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalStorageSlot;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalStorageTabClient;
import org.cyclops.integratedterminals.capability.ingredient.IngredientComponentViewHandlerConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A client-side storage terminal ingredient tab.
 * @param <T> The instance type.
 * @param <M> The matching condition parameter.
 * @author rubensworks
 */
public class TerminalStorageTabIngredientComponentClient<T, M> implements ITerminalStorageTabClient {

    private final IngredientComponent<T, M> ingredientComponent;
    private final IIngredientComponentViewHandler<T, M> ingredientComponentViewHandler;
    private final ItemStack icon;

    private final TIntObjectMap<IIngredientListMutable<T, M>> ingredientsViews;

    private TIntLongMap maxQuantities;
    private TIntLongMap totalQuantities;

    public TerminalStorageTabIngredientComponentClient(IngredientComponent<?, ?> ingredientComponent) {
        this.ingredientComponent = (IngredientComponent<T, M>) ingredientComponent;
        this.ingredientComponentViewHandler = Objects.requireNonNull(this.ingredientComponent.getCapability(IngredientComponentViewHandlerConfig.CAPABILITY));
        this.icon = ingredientComponentViewHandler.getIcon();

        this.ingredientsViews = new TIntObjectHashMap<>();

        this.maxQuantities = new TIntLongHashMap();
        this.totalQuantities = new TIntLongHashMap();
    }

    @Override
    public String getId() {
        return ingredientComponent.getName().toString();
    }

    @Override
    public ItemStack getIcon() {
        return this.icon;
    }

    @Override
    public List<String> getTooltip() {
        return Lists.newArrayList(L10NHelpers.localize("gui.integratedterminals.terminal_storage.storage_name",
                L10NHelpers.localize(this.ingredientComponent.getUnlocalizedName())));
    }

    protected IIngredientListMutable<T, M> getSafeIngredientsView(int channel) {
        IIngredientListMutable<T, M> ingredientsView = ingredientsViews.get(channel);
        if (ingredientsView == null) {
            ingredientsView = new IngredientArrayList<>(this.ingredientComponent);
            ingredientsViews.put(channel, ingredientsView);
        }
        return ingredientsView;
    }

    @Override
    public List<ITerminalStorageSlot> getSlots(int channel, int offset, int limit) {
        IIngredientListMutable<T, M> ingredients = getSafeIngredientsView(channel);
        return ingredients.subList(offset, Math.min(limit, ingredients.size())).stream()
                .map(instance -> new TerminalStorageSlotIngredient<>(ingredientComponentViewHandler, instance))
                .collect(Collectors.toList());
    }

    @Override
    public int getSlotCount(int channel) {
        return getSafeIngredientsView(channel).size();
    }

    @Override
    public String getStatus(int channel) {
        return String.format("%,d / %,d", getTotalQuantity(channel), getMaxQuantity(channel));
    }

    /**
     * Receiver an ingredients change event.
     * @param channel A channel id.
     * @param changeType A change type.
     * @param ingredients A list of changed ingredients.
     */
    public void onChange(int channel, IIngredientComponentStorageObservable.Change changeType, IngredientArrayList<T, M> ingredients) {
        // Apply the change to the wildcard channel as well
        if (channel != IPositionedAddonsNetwork.WILDCARD_CHANNEL) {
            onChange(IPositionedAddonsNetwork.WILDCARD_CHANNEL, changeType, ingredients);
        }

        // Calculate quantity-diff
        long quantity = 0;
        IIngredientMatcher<T, M> matcher = ingredients.getComponent().getMatcher();
        for (T ingredient : ingredients) {
            quantity += matcher.getQuantity(ingredient);
        }

        // Use a prototype-based collection so that ingredients are collapsed
        IIngredientListMutable<T, M> persistedIngredients = getSafeIngredientsView(channel);
        IIngredientCollapsedCollectionMutable<T, M> prototypedIngredients = new IngredientCollectionPrototypeMap<>(this.ingredientComponent);
        prototypedIngredients.addAll(persistedIngredients);

        // Apply changes
        if (changeType == IIngredientComponentStorageObservable.Change.ADDITION) {
            prototypedIngredients.addAll(ingredients);
        } else {
            prototypedIngredients.removeAll(ingredients);
            quantity = -quantity;
        }

        // Persist changes
        persistedIngredients.clear();
        persistedIngredients.addAll(prototypedIngredients);

        long newQuantity = totalQuantities.get(channel) + quantity;
        if (newQuantity != 0) {
            totalQuantities.put(channel, newQuantity);
        }
    }

    /**
     * Get the total maximum allowed quantity in the given channel.
     * @param channel A channel id.
     * @return The max quantity.
     */
    public long getMaxQuantity(int channel) {
        // Take the sum of all channels when requesting wildcard channel
        if (channel == IPositionedAddonsNetwork.WILDCARD_CHANNEL) {
            return Arrays.stream(getChannels()).mapToLong(this::getMaxQuantity).sum();
        }
        return maxQuantities.get(channel);
    }

    /**
     * Set the max quantity in the given channel.
     * @param channel A channel id.
     * @param maxQuantity The new max quantity.
     */
    public void setMaxQuantity(int channel, long maxQuantity) {
        this.maxQuantities.put(channel, maxQuantity);
    }

    /**
     * Get the total instance quantities in the given channel.
     * @param channel A channel id.
     * @return The total quantity.
     */
    public long getTotalQuantity(int channel) {
        return totalQuantities.get(channel);
    }

    @Override
    public int[] getChannels() {
        int[] channels = maxQuantities.keys();
        Arrays.sort(channels);
        return channels;
    }
}