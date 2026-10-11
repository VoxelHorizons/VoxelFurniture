package org.voxelhorizons.furniture;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureDefinitionParser;
import org.voxelhorizons.furniture.model.FurnitureRendererType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.Assert.*;

/** Regression tests for the hotspot sampled in FurnitureManager.animateIdleDisplays. */
public class FurnitureDefinitionCacheTest {
    private static final ContentID OAK=ContentID.parse("voxel:oak_table","voxel");
    private static final ContentID VARIANT=ContentID.parse("voxel:oak_table_side","voxel");
    private static final ContentID UNKNOWN=ContentID.parse("voxel:missing_furniture","voxel");

    private static ItemDefinition furniture(ContentID id,boolean abstractItem,float spin){
        Map<String,Object> idle=new HashMap<String,Object>();
        Map<String,Object> spinConfig=new HashMap<String,Object>();
        spinConfig.put("degrees_per_tick",Float.valueOf(spin));
        idle.put("spin",spinConfig);
        Map<String,Object> settings=new HashMap<String,Object>();
        settings.put("renderer","DISPLAY");
        settings.put("idle_animation",idle);
        Map<String,Object> properties=new HashMap<String,Object>();
        properties.put("furniture",settings);
        return new ItemDefinition(id,null,"PAPER","test",Collections.<String>emptyList(),
                true,abstractItem,Optional.<ContentID>empty(),null,properties);
    }

    private static FurnitureDefinitionCache cache(Map<ContentID,ItemDefinition> items,
            final long[] revision,final AtomicInteger parses){
        FurnitureDefinitionParser real=new FurnitureDefinitionParser(FurnitureRendererType.AUTO,45.0F);
        Function<ItemDefinition,Optional<FurnitureDefinition>> counting=item->{
            parses.incrementAndGet();
            return real.parse(item);
        };
        return new FurnitureDefinitionCache(id->Optional.ofNullable(items.get(id)),counting,
                ()->revision[0]);
    }

    @Test public void animationBaseAndAbstractRenderVariantParseOncePerRevision(){
        Map<ContentID,ItemDefinition> items=new HashMap<ContentID,ItemDefinition>();
        items.put(OAK,furniture(OAK,false,0));
        items.put(VARIANT,furniture(VARIANT,true,4));
        long[] revision={10L};
        AtomicInteger parses=new AtomicInteger();
        FurnitureDefinitionCache cache=cache(items,revision,parses);

        FurnitureDefinition base=cache.definition(OAK).get();
        FurnitureDefinition visual=cache.renderDefinition(VARIANT,base);
        assertEquals(4.0F,visual.idleAnimation().spinDegreesPerTick(),0.0001F);
        assertFalse(cache.definition(VARIANT).isPresent()); // abstract variants aren't placeable

        for(int tick=0;tick<1200;tick++){
            assertSame(base,cache.definition(OAK).get());
            assertSame(visual,cache.renderDefinition(VARIANT,base));
        }
        assertEquals("1200 idle animation ticks must not reparse",2,parses.get());
    }

    @Test public void placeableLookupNeverParsesAbstractContent(){
        Map<ContentID,ItemDefinition> items=new HashMap<ContentID,ItemDefinition>();
        items.put(VARIANT,furniture(VARIANT,true,2));
        final AtomicInteger parses=new AtomicInteger();
        FurnitureDefinitionCache cache=new FurnitureDefinitionCache(
                id->Optional.ofNullable(items.get(id)),
                item->{parses.incrementAndGet();throw new IllegalArgumentException("invalid model variant");},
                ()->123L);
        assertFalse(cache.definition(VARIANT).isPresent());
        assertFalse(cache.definition(VARIANT).isPresent());
        assertEquals("abstract entries must not be validated as placeable",0,parses.get());
        try {
            cache.renderDefinition(VARIANT,null);
            fail("rendering should still report invalid abstract content");
        } catch (IllegalArgumentException expected){
            assertEquals("invalid model variant",expected.getMessage());
        }
        assertEquals(1,parses.get());
    }

    @Test public void changingContentRevisionInvalidatesBothBaseAndVariant(){
        Map<ContentID,ItemDefinition> items=new HashMap<ContentID,ItemDefinition>();
        items.put(OAK,furniture(OAK,false,1));
        items.put(VARIANT,furniture(VARIANT,true,2));
        long[] revision={5L};
        AtomicInteger parses=new AtomicInteger();
        FurnitureDefinitionCache cache=cache(items,revision,parses);
        FurnitureDefinition oldBase=cache.definition(OAK).get();
        FurnitureDefinition oldVisual=cache.renderDefinition(VARIANT,oldBase);
        assertEquals(2,parses.get());

        items.put(OAK,furniture(OAK,false,5));
        items.put(VARIANT,furniture(VARIANT,true,7));
        assertSame("unchanged revision must preserve cached value",oldBase,cache.definition(OAK).get());
        revision[0]=6L;

        FurnitureDefinition newBase=cache.definition(OAK).get();
        FurnitureDefinition newVisual=cache.renderDefinition(VARIANT,newBase);
        assertNotSame(oldBase,newBase);
        assertNotSame(oldVisual,newVisual);
        assertEquals(5.0F,newBase.idleAnimation().spinDegreesPerTick(),0.0001F);
        assertEquals(7.0F,newVisual.idleAnimation().spinDegreesPerTick(),0.0001F);
        assertEquals(4,parses.get());
    }

    @Test public void missingDefinitionsAreCachedAndRecheckedAfterReload(){
        Map<ContentID,ItemDefinition> items=new HashMap<ContentID,ItemDefinition>();
        long[] revision={42L};
        AtomicInteger lookups=new AtomicInteger();
        AtomicInteger parses=new AtomicInteger();
        FurnitureDefinitionCache cache=new FurnitureDefinitionCache(id->{
            lookups.incrementAndGet();
            return Optional.ofNullable(items.get(id));
        },new FurnitureDefinitionParser(FurnitureRendererType.AUTO,45.0F)::parse,
                ()->revision[0]);

        assertFalse(cache.definition(UNKNOWN).isPresent());
        assertFalse(cache.definition(UNKNOWN).isPresent());
        assertEquals(1,lookups.get());
        items.put(UNKNOWN,furniture(UNKNOWN,false,3));
        revision[0]=43L;
        assertTrue(cache.definition(UNKNOWN).isPresent());
        assertEquals(2,lookups.get());
    }

    @Test public void missingOrNonFurnitureRenderVariantsPreserveBaseFallback(){
        Map<ContentID,ItemDefinition> items=new HashMap<ContentID,ItemDefinition>();
        items.put(OAK,furniture(OAK,false,1));
        items.put(VARIANT,new ItemDefinition(VARIANT,null,"PAPER","plain",
                Collections.<String>emptyList(),true,Optional.<ContentID>empty(),null,
                Collections.<String,Object>emptyMap()));
        long[] revision={1L};
        FurnitureDefinitionCache cache=cache(items,revision,new AtomicInteger());
        FurnitureDefinition base=cache.definition(OAK).get();
        assertSame(base,cache.renderDefinition(VARIANT,base));
        assertSame(base,cache.renderDefinition(UNKNOWN,base));
    }

    @Test public void exceptionsDoNotPoisonTheCache(){
        Map<ContentID,ItemDefinition> items=new HashMap<ContentID,ItemDefinition>();
        items.put(OAK,furniture(OAK,false,1));
        AtomicInteger attempts=new AtomicInteger();
        FurnitureDefinitionParser real=new FurnitureDefinitionParser(FurnitureRendererType.AUTO,45.0F);
        FurnitureDefinitionCache cache=new FurnitureDefinitionCache(
                id->Optional.ofNullable(items.get(id)),item->{
                    if(attempts.incrementAndGet()==1)throw new IllegalArgumentException("bad data");
                    return real.parse(item);
                },()->1L);
        try{
            cache.definition(OAK);
            fail("first parse should fail");
        }catch(IllegalArgumentException expected){
            assertEquals("bad data",expected.getMessage());
        }
        assertTrue(cache.definition(OAK).isPresent());
        assertEquals(2,attempts.get());
    }
}
