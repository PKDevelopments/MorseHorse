package p.k.morsehorse;

import static android.view.View.VISIBLE;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.slider.Slider;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    //Booleans for light flashing
    boolean signaling;
    boolean flash_on;
    boolean long_flash;
    boolean short_flash;
    boolean initialized = false;
    boolean emergency = false;
    //Char array
    ArrayList<Character> signal_chars = new ArrayList<>();
    String transmitted_message = " ";
    int char_index;
    //Flashlight Variables
    CameraManager cm;
    String cameraID;
    //Handler Variables
    private final android.os.Handler handler = new android.os.Handler();
    //Layout Variables
    TextView title;
    TextView status;
    TextView decoder;
    EditText transmitview;
    Button signal_button;
    boolean main_menu;
    //Time keeping variables
    int FRAME_RATE = 400;
    int count = 0;
    int ONE_INTERVAL = 2;
    int PAUSE_INTERVAL = ONE_INTERVAL*2;
    int current_char_count = 0;
    //SharedPreferences variables
    SharedPreferences savedata;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        set_settings();
        Initialize();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.codetable:
                if(!signaling) {
                    emergency = false;
                    viewtable();
                }else{
                    Toast.makeText(getBaseContext(),"Transmission in progress.",Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.emergency:
                if(!signaling && main_menu) {
                    Toast.makeText(getBaseContext(),"Sending SOS.",Toast.LENGTH_SHORT).show();
                    send_signal("SOS");
                    emergency = true;
                    signal_button = findViewById(R.id.signal_button);
                    signal_button.setText("Stop Signaling");
                }else{
                    if(!main_menu){
                        setContentView(R.layout.activity_main);
                        Initialize();
                    }
                    if(signaling){
                        Toast.makeText(getBaseContext(),"Stopping current signal to transmit SOS.",Toast.LENGTH_SHORT).show();
                        stop_signal();}
                        send_signal("SOS");
                        signal_button = findViewById(R.id.signal_button);
                        signal_button.setText("Stop Signaling");
                        emergency = true;
                }
                break;
            case R.id.settings:
                if(!signaling) {
                    emergency = false;
                    viewsettings();
                }else{
                    Toast.makeText(getBaseContext(),"Transmission in progress.",Toast.LENGTH_SHORT).show();
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    //App Loop
    private Runnable updater = new Runnable(){
        public void run() {
            if(signaling && signal_chars.size() > 0){
                char current_char = signal_chars.get(char_index);
                int[] array = getMorseArray(current_char);
                int size = array.length;
                count++;
                if(count % ONE_INTERVAL == 0 && count > 0){
                    if(flash_on){
                        if(long_flash && count == ONE_INTERVAL*2){
                            count = 0;
                            flash_on = false;
                            long_flash = false;
                        }
                        if(short_flash && count == ONE_INTERVAL){
                            count = 0;
                            flash_on = false;
                            short_flash = false;
                        }
                    }
                    if(!flash_on && count > 0){
                        if(count == ONE_INTERVAL && current_char_count < array.length-1){
                            //Determine next flash based on the current char
                            current_char_count++;
                            if(current_char_count < array.length){
                                count = 0;
                                switch(array[current_char_count]){
                                    case 0: count = PAUSE_INTERVAL; current_char_count = array.length-1; break;
                                    case 1: short_flash = true; break;
                                    case 2: long_flash = true; break;
                                }
                                //turn light on
                                if(short_flash || long_flash){
                                    flash_on = true;
                                    turn_on_light();}
                            }
                        }
                        if(count >= PAUSE_INTERVAL && current_char_count < array.length){
                            String s = " ";
                            count = 0;
                            current_char_count++;
                            //Determine next character
                            if(current_char_count >= array.length){
                                char_index++;
                                if(!getCompletionStatus(char_index)){
                                    current_char_count = 0;
                                    transmitted_message+=signal_chars.get(char_index);
                                    current_char = signal_chars.get(char_index);
                                    array = getMorseArray(current_char);
                                    switch(array[current_char_count]){
                                        case 1: short_flash = true; break;
                                        case 2: long_flash = true; break;
                                    }
                                    //turn light on
                                    if(short_flash || long_flash){flash_on = true;
                                        turn_on_light();}
                                }
                            }
                        }
                    }
                }
                if(!flash_on){
                    turn_off_light();
                }
                //status.setText("Count: "+count+", Flash: "+flash_on+" Current Char: "+current_char);
                status.setText("Transmitted:"+transmitted_message);
                decoder.setText(current_char+": "+getDecodedChar(current_char));
            }
            handler.postDelayed(this, FRAME_RATE);
        }
    };

    public boolean getCompletionStatus(int index){
        if(index == signal_chars.size()){
            reset_signaling();
            return true;
        }
        return false;
    }

    public String getDecodedChar(char input) {
        String decoded_string = " ";
        int[] decoder = getMorseArray(input);
        for(int i = 0; i < decoder.length; i++){
            switch(decoder[i]){
                case 1:
                    decoded_string+="_ ";
                    break;
                case 2:
                    decoded_string+="__ ";
                    break;
            }
        }
        return decoded_string;
    }

    public int[] getMorseArray(char character){
        int[] arr = new int[5];
        switch(character){
            case '.': case ',': case '?': case '/': case '!':
            case '@': case '#': case '$': case '%': case '^':
            case '&': case '*': case '(': case ')': case '-':
            case '_': case '+': case '=': case '~': case '|':
            case '[': case ']': case '{': case '}': case ' ':
                for(int i = 0; i < 5; i++){arr[i] = 0;}
                break;
            case 'a': case 'A':
                arr = getResources().getIntArray(R.array.A);
                break;
            case 'b': case 'B':
                arr = getResources().getIntArray(R.array.B);
                break;
            case 'c': case 'C':
                arr = getResources().getIntArray(R.array.C);
                break;
            case 'd': case 'D':
                arr = getResources().getIntArray(R.array.D);
                break;
            case 'e': case 'E':
                arr = getResources().getIntArray(R.array.E);
                break;
            case 'f': case 'F':
                arr = getResources().getIntArray(R.array.F);
                break;
            case 'g': case 'G':
                arr = getResources().getIntArray(R.array.G);
                break;
            case 'h': case 'H':
                arr = getResources().getIntArray(R.array.H);
                break;
            case 'i': case 'I':
                arr = getResources().getIntArray(R.array.I);
                break;
            case 'j': case 'J':
                arr = getResources().getIntArray(R.array.J);
                break;
            case 'k': case 'K':
                arr = getResources().getIntArray(R.array.K);
                break;
            case 'l': case 'L':
                arr = getResources().getIntArray(R.array.L);
                break;
            case 'm': case 'M':
                arr = getResources().getIntArray(R.array.M);
                break;
            case 'n': case 'N':
                arr = getResources().getIntArray(R.array.N);
                break;
            case 'o': case 'O':
                arr = getResources().getIntArray(R.array.O);
                break;
            case 'p': case 'P':
                arr = getResources().getIntArray(R.array.P);
                break;
            case 'q': case 'Q':
                arr = getResources().getIntArray(R.array.Q);
                break;
            case 'r': case 'R':
                arr = getResources().getIntArray(R.array.R);
                break;
            case 's': case 'S':
                arr = getResources().getIntArray(R.array.S);
                break;
            case 't': case 'T':
                arr = getResources().getIntArray(R.array.T);
                break;
            case 'u': case 'U':
                arr = getResources().getIntArray(R.array.U);
                break;
            case 'v': case 'V':
                arr = getResources().getIntArray(R.array.V);
                break;
            case 'w': case 'W':
                arr = getResources().getIntArray(R.array.W);
                break;
            case 'x': case 'X':
                arr = getResources().getIntArray(R.array.X);
                break;
            case 'y': case 'Y':
                arr = getResources().getIntArray(R.array.Y);
                break;
            case 'z': case 'Z':
                arr = getResources().getIntArray(R.array.Z);
                break;
            case '1':
                arr = getResources().getIntArray(R.array.ONE);
                break;
            case '2':
                arr = getResources().getIntArray(R.array.TWO);
                break;
            case '3':
                arr = getResources().getIntArray(R.array.THREE);
                break;
            case '4':
                arr = getResources().getIntArray(R.array.FOUR);
                break;
            case '5':
                arr = getResources().getIntArray(R.array.FIVE);
                break;
            case '6':
                arr = getResources().getIntArray(R.array.SIX);
                break;
            case '7':
                arr = getResources().getIntArray(R.array.SEVEN);
                break;
            case '8':
                arr = getResources().getIntArray(R.array.EIGHT);
                break;
            case '9':
                arr = getResources().getIntArray(R.array.NINE);
                break;
            case '0':
                arr = getResources().getIntArray(R.array.ZERO);
                break;
        }
        return arr;
    }

    public void Initialize(){
        main_menu = true;
        signal_button = findViewById(R.id.signal_button);
        transmitview = findViewById(R.id.transmittext);
        signal_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!signaling){
                    final String input = transmitview.getText().toString();
                    if(!input.equals("")){
                        send_signal(input);
                        signal_button.setText("Stop Signaling");
                    }
                    else{
                        Toast.makeText(getBaseContext(),"Enter an input to transmit.",Toast.LENGTH_SHORT).show();
                    }
                }
                else{
                    emergency = false;
                    stop_signal();
                }
            }
        });
        title = findViewById(R.id.title_view);
        decoder = findViewById(R.id.decodeview);
        status = findViewById(R.id.status_panel);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // O means back camera unit,
            // 1 means front camera unit
            cameraID = cm.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void reset_signaling(){
        signal_button = findViewById(R.id.signal_button);
        signal_chars.clear();
        count = 0;
        current_char_count = 0;
        Toast.makeText(this,"Transmission Complete.",Toast.LENGTH_SHORT).show();
        if(!emergency){
            signal_button.setText("Begin Signaling");
            status.setText("No signals in progress.");
            decoder.setText(" ");
            signaling = false;
        }
        else{
            send_signal("SOS");
        }
        char_index = 0;
    }

    public void savechanges(int framerate){
        savedata = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = savedata.edit();
        editor.putInt("framerate",framerate);
        editor.apply();
    }

    public void send_signal(String input){
        //Convert String into char array
        for(int i = 0; i < input.length(); i++){
            signal_chars.add(input.charAt(i));
        }
        //Determine whether long_flash or short_flash is true
        int[] array = getMorseArray(signal_chars.get(0));
        switch(array[0]){
            //Immediately skip spaces
            case 0: short_flash = false; long_flash = false; turn_off_light(); break;
            case 1: short_flash = true; flash_on = true; turn_on_light(); break;
            case 2: long_flash = true; flash_on = true; turn_on_light(); break;
        }
        //Turn on light
        signaling = true;
        transmitted_message = " ";
        Toast.makeText(getBaseContext(),"Sending: "+input,Toast.LENGTH_SHORT).show();
        if(!initialized){handler.postDelayed(updater, FRAME_RATE); initialized = true;}
        transmitted_message+=signal_chars.get(0);
    }


    public void set_settings(){
        savedata = this.getPreferences(Context.MODE_PRIVATE);
        FRAME_RATE = savedata.getInt("framerate",250);
    }

    public void stop_signal(){
        Toast.makeText(getBaseContext(),"Signal Stopped.",Toast.LENGTH_SHORT).show();
        turn_off_light();
        reset_signaling();
        //handler.removeCallbacks(updater);
    }

    public void turn_off_light(){
        try {
            cm.setTorchMode(cameraID, false);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void turn_on_light(){
        try {
            cm.setTorchMode(cameraID, true);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void viewsettings(){
        main_menu = false;
        setContentView(R.layout.settings_view);
        Slider slider = findViewById(R.id.slider);
        TextView subtext = findViewById(R.id.subtext1);
        Button savebutton;
        Button backbutton;
        slider.setValue((float)FRAME_RATE);
        subtext.setText("Short signals: "+FRAME_RATE*2+" ms\nLong signals: "+FRAME_RATE*4+" ms");
        slider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                subtext.setText("Short signals: "+(int)value*2+" ms\nLong signals: "+(int)value*4+" ms");
            }
        });
        savebutton = findViewById(R.id.savebutton);
        savebutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Set new framerate
                FRAME_RATE = (int)slider.getValue();
                //Save Values
                savechanges(FRAME_RATE);
                Toast.makeText(getBaseContext(),"Changes saved.",Toast.LENGTH_SHORT).show();
            }
        });
        backbutton = findViewById(R.id.backbutton);
        backbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setContentView(R.layout.activity_main);
                Initialize();
            }
        });

    }

    public void viewtable(){
        main_menu = false;
        setContentView(R.layout.table_view);
        Button backbutton = findViewById(R.id.backbutton);
        backbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setContentView(R.layout.activity_main);
                Initialize();
            }
        });
    }
}