package engifinal.speedshield;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class profile extends AppCompatActivity {

    private long[] vals;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView lifetimePointsTextView = (TextView) findViewById(R.id.profile_lifetime_points);
        TextView currentPointsTextView = (TextView) findViewById(R.id.profile_current_points);
        TextView milesDrivenTextView = (TextView) findViewById(R.id.profile_miles_driven);

        File historyFile = new File(getFilesDir() + "/history.txt");
        if (historyFile.isFile()) {
            try {
                FileInputStream fIn = new FileInputStream(historyFile);
                BufferedReader input = new BufferedReader(new InputStreamReader(fIn));
                lifetimePointsTextView.setText("Lifetime points: " + input.readLine());
                currentPointsTextView.setText("Current points: " + input.readLine());
                milesDrivenTextView.setText("Miles driven: " + input.readLine());
                for (int i = 0; i < 3; i++)
                {
                    vals[i] = Long.parseLong(input.readLine());
                }
                input.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            System.out.println("didn't find history file. creating new history file at " +
                    historyFile.toString());

            try {
                historyFile.createNewFile();
                FileOutputStream fos = new FileOutputStream(historyFile);
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

                bw.write("1561");
                bw.newLine();
                bw.write("189");
                bw.newLine();
                bw.write("0");

                bw.close();
            }
            catch(Exception e)
            {
                e.printStackTrace();
                System.out.println("history file failed to create for some reason!");
            }
        }
    }
}
