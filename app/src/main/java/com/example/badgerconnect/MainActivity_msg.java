package com.example.badgerconnect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.os.Bundle;
import android.widget.Button;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.badgerconnect.Fragments.UsersFragment;
import com.example.badgerconnect.Model.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.rengwuxian.materialedittext.MaterialEditText;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity_msg extends AppCompatActivity {
    MaterialEditText username, email, password;
    Button btn_login;
    FirebaseAuth auth;
    public static int Mid=0;

   // CircleImageView profile_image;
    ImageView profile_image;
    ActionMenuItemView profile_image_menu; //USE FOR NOW
    DatabaseReference reference;
    FirebaseUser firebaseUser;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_msg);

        auth=FirebaseAuth.getInstance();

        String email1 = "test1@gmail.com";
        String email2 = "cbfu@wisc.edu";
        String password = "000000";

        //auth.signOut();
        //System.out.println("About to sign in");
        auth.signInWithEmailAndPassword(email2, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    // Sign in success, update UI with the signed-in user's information
                    System.out.println("Sign in success");
                    FirebaseUser user = auth.getCurrentUser();
                    // Do something with the user object
                } else {
                    // If sign in fails, display a message to the user.
                    System.out.println("Sign in failed");
                    Toast.makeText(MainActivity_msg.this, "Authentication failed.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        //System.out.println("Past sign in");


        firebaseUser=FirebaseAuth.getInstance().getCurrentUser();
        //reference= FirebaseDatabase.getInstance().getReference("Users").child(firebaseUser.getUid());

        reference= FirebaseDatabase.getInstance().getReference("Data").child("Users");


        //Set up tab layout feature to display list of users
        TabLayout tabLayout= findViewById(R.id.tab_layout);
        ViewPager viewPager= findViewById(R.id.view_pager);

        ViewPagerAdapter viewPagerAdapter= new ViewPagerAdapter(getSupportFragmentManager());
        //viewPagerAdapter.addFragment(new ChatsFragment(), "Chats");
        viewPagerAdapter.addFragment(new UsersFragment(), "Users");
        viewPager.setAdapter(viewPagerAdapter);
        tabLayout.setupWithViewPager(viewPager);

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        firebaseUser=FirebaseAuth.getInstance().getCurrentUser();
        reference= FirebaseDatabase.getInstance().getReference("Data").child("Users");
        Query query= reference;
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot datasnapshot) {
                profile_image_menu= findViewById(R.id.profile_image_menu);
                username = findViewById(R.id.username);

                User user = new User();
                for (DataSnapshot userinfo : datasnapshot.getChildren()) {
                    user = userinfo.getValue(User.class);
                    if (user.getUid().equals(firebaseUser.getUid())) {
                        if (user.getProfile_pic().equals("default")) {
                            profile_image.setImageResource(R.mipmap.ic_launcher);
                        } else {

                            MenuItem profileMenuItem = menu.findItem(R.id.profile_image_menu);
                            MenuItem username_menu=menu.findItem(R.id.username);
                            username_menu.setTitle(user.getName());

                            // Load the image from the URL using Glide
                            Glide.with(MainActivity_msg.this)
                                    .asBitmap()
                                    .load(user.getProfile_pic())
                                    .into(new CustomTarget<Bitmap>() {
                                        @Override
                                        public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                                            // Set the loaded image as the icon of the menu item
                                            profileMenuItem.setIcon(new BitmapDrawable(getResources(), bitmap));
                                        }
                                        @Override
                                        public void onLoadCleared(@Nullable Drawable placeholder) {
                                            // Called when the resource is freed
                                        }
                                    });

                        }
                    }
                }
            }


        @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        //return super.onOptionsItemSelected(item);
        switch (item.getItemId()){

            case R.id.logout:
                FirebaseAuth.getInstance().signOut();

                finish();
                return true;
        }
        return false;
    }

    class ViewPagerAdapter extends FragmentPagerAdapter{

        private ArrayList<Fragment> fragments;
        private ArrayList<String> titles;

        ViewPagerAdapter(FragmentManager fm){
            super(fm);
            this.fragments= new ArrayList<>();
            this.titles= new ArrayList<>();
        }
        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragments.get(position);
        }

        @Override
        public int getCount() {
            return fragments.size();
        }

        public void addFragment (Fragment fragment, String title){
            fragments.add(fragment);
            titles.add(title);
        }
        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return titles.get(position);
        }
    }
}