package com.andres.zengecko;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class ProfileActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        hideBars();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        root.setBackgroundColor(getColor(R.color.zen_bg));

        TextView avatar = new TextView(this);
        avatar.setText("👤");
        avatar.setTextSize(42);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundResource(R.drawable.bg_favicon);
        root.addView(avatar, new LinearLayout.LayoutParams(dp(82), dp(82)));

        TextView title = new TextView(this);
        title.setText("Perfil local");
        title.setTextSize(23);
        title.setTextColor(getColor(R.color.zen_text));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        root.addView(title, titleParams);

        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Tu nombre");
        name.setTextColor(getColor(R.color.zen_text));
        name.setHintTextColor(getColor(R.color.zen_muted));
        name.setBackgroundResource(R.drawable.bg_address);
        name.setPadding(dp(16), 0, dp(16), 0);
        name.setText(getSharedPreferences("profile", Context.MODE_PRIVATE).getString("name", ""));
        root.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView note = new TextView(this);
        note.setText("Este perfil se guarda únicamente en el dispositivo. La sincronización llegará en una etapa posterior.");
        note.setTextSize(13);
        note.setTextColor(getColor(R.color.zen_muted));
        note.setPadding(dp(4), dp(14), dp(4), dp(18));
        root.addView(note);

        Button save = new Button(this);
        save.setText("Guardar perfil");
        save.setOnClickListener(v -> {
            getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
                    .putString("name", name.getText().toString().trim()).apply();
            Toast.makeText(this, "Perfil guardado", Toast.LENGTH_SHORT).show();
        });
        root.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); hideBars(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void hideBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        }
    }
}
