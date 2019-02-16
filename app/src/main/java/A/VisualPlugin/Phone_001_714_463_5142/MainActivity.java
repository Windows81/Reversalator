package A.VisualPlugin.Phone_001_714_463_5142;

import android.app.Application;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {
	static Application MainContext;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		MainContext = getApplication();
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.activity_main);
		new AlertDialog.Builder(this).setTitle("Argh").setMessage("Watch out!").setNeutralButton("Close", null).show();
		StaticMethods.getAlbum("kanye west", "ye");
	}
}
