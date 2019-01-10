package ideum.com.megamovie.Java.LocationAndTiming;

import android.content.Context;
import android.content.res.AssetManager;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import ideum.com.megamovie.Java.Application.TimingDataReaderWriter;


public class EclipseTimes {
    public enum Phase {c1,c2,cm,c3,c4};

    private static final String DATA_PATH = "timing_files";
    private static final double LAT_LNG_INTERVAL = 0.01;
    EnumMap<Phase, List<EclipseTimingPatch>> patches = new EnumMap<>(Phase.class);

    public Long GetEclipseTime(Phase phase, LatLng p) {
        for(EclipseTimingPatch patch : patches.get(phase)) {
            if(patch.contains(p)){
                return patch.getEclipseTimeMills(p);
            }
        }
        return null;
    }



    public EclipseTimes(Context context) throws IOException {

        patches.put(Phase.c1, new ArrayList<EclipseTimingPatch>());
        patches.put(Phase.c2, new ArrayList<EclipseTimingPatch>());
        patches.put(Phase.cm, new ArrayList<EclipseTimingPatch>());
        patches.put(Phase.c3, new ArrayList<EclipseTimingPatch>());
        patches.put(Phase.c4, new ArrayList<EclipseTimingPatch>());

        AssetManager assetManager = context.getAssets();
        String[] files = assetManager.list(DATA_PATH);
        for (int i = 0; i < files.length; i++) {
            String file = files[i];
            String[] parts = file.split("_");

            String contact = parts[0];
            try {
                Phase phase = Phase.valueOf(contact);

                Double lngMin = -Double.valueOf(parts[1]);
                Double lngMax = -Double.valueOf(parts[2]);
                Double latMin = Double.valueOf(parts[3]);
                Double latMax = Double.valueOf(parts[4]);

                InputStream inputStream = assetManager.open(DATA_PATH + "/" + file);
                int[] ints = TimingDataReaderWriter.readIntsFromStream(inputStream);
                EclipseTimingPatch patch = new EclipseTimingPatch(latMin,latMax,lngMin,lngMax, LAT_LNG_INTERVAL,ints);
                patches.get(phase).add(patch);

            } catch(IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }
}
