package com.example.badgerconnect.Fragments;

import static android.content.Context.LOCATION_SERVICE;
import static com.example.badgerconnect.DatabaseFunctions.readUserData;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.badgerconnect.LoginActivity;
import com.example.badgerconnect.R;
import com.example.badgerconnect.UserInfo;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


/**
 * A simple {@link Fragment} subclass.
 * Use the  factory method to
 * create an instance of this fragment.
 */
public class MapsFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;
    private DatabaseReference databaseReference;
    private DatabaseReference databaseRef;
    private FirebaseAuth mAuth;
    private static final String TAG = "EmailPassword";
    private final long MIN_TIME = 100;
    private final long MIN_DIST = 25;

    private Marker previousMarker;
    private Location previousLocation;
    private EditText editTextLatitude;
    private EditText editTextLongitude;

    private Button removeMarkerButton;


    private MarkerOptions markerOptions;

    private List<Marker> markerList = new ArrayList<>();

    private Set<String> removedMarkerTitles = new HashSet<>();
    private UserInfo user = new UserInfo();
    private String username;
    private String major;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mAuth = FirebaseAuth.getInstance();
        View view = inflater.inflate(R.layout.fragment_maps, container, false);

        removeMarkerButton = view.findViewById(R.id.remove_marker_button);
        // Set a click listener on the button to display a dialog

        // Get references to UI elements
        editTextLatitude = view.findViewById(R.id.editText);
        editTextLatitude.setVisibility(View.GONE);
        editTextLongitude = view.findViewById(R.id.editText2);
        editTextLongitude.setVisibility(View.GONE);
        // Initialize Firebase Database reference
        databaseReference = FirebaseDatabase.getInstance().getReference("Data/Map");
        databaseRef= FirebaseDatabase.getInstance().getReference("Data/markers");
        // Check if user is already signed in

        if (mAuth.getCurrentUser() != null) {
            // User is signed in, display map
            CompletableFuture<UserInfo> currUserInfo = readUserData(mAuth.getCurrentUser().getUid(), user);
            currUserInfo.thenAccept(user -> {
                major = user.getMajor();
                username = user.getUsername();
                setUpMap();
            });
        }
        else {
            showSignInScreen();
        }

        Button removeMarkerButton = view.findViewById(R.id.remove_marker_button);
        removeMarkerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create a list of marker titles
                List<String> markerTitles = new ArrayList<>();
                for (Marker marker : markerList) {
                    markerTitles.add(marker.getTitle());
                }

                // Display the list in a dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Select a marker to remove");
                builder.setItems(markerTitles.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Remove the marker from the map
                        Marker marker = markerList.get(which);
                        marker.remove();
                        markerList.remove(which);

                        // Remove the marker from Firebase
                        removeMarkerFromFirebase(marker);
                    }
                });
                builder.show();
            }
        });
        return view;
    }

    private void showSignInScreen() {
        Intent signInIntent = new Intent(getContext(), getActivity().getClass());
        signInIntent.setClass(getContext(), LoginActivity.class);
        startActivity(signInIntent);


        getActivity().finish();
        previousLocation = null;
    }


    private void removeMarkerFromFirebase(Marker marker) {

        Query query = databaseRef.orderByChild("title").equalTo(marker.getTitle());

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    snapshot.getRef().removeValue();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to query markers from Firebase database", databaseError.toException());
            }
        });


    }

    private void setUpMap() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used
        SupportMapFragment mapFragment = (SupportMapFragment) getActivity().getSupportFragmentManager().getFragments().get(0).getChildFragmentManager().getFragments().get(0);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        String uid = mAuth.getCurrentUser().getUid();
        DatabaseReference userLocationReference = databaseReference.child(uid);
        DatabaseReference eventRef = databaseReference.child(uid).child("Events");

        userLocationReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                mMap.clear(); // 

                try {
                    String databaseLatitudeString = dataSnapshot.child("user_latitude").getValue().toString();
                    String databaseLongitudedeString = dataSnapshot.child("user_longitude").getValue().toString();

                    LatLng latLng = new LatLng(Double.parseDouble(databaseLatitudeString), Double.parseDouble(databaseLongitudedeString));

                    if (previousMarker != null) {
                        previousMarker.remove(); // remove previous marker
                    }
                    previousMarker = mMap.addMarker(new MarkerOptions().position(latLng).snippet("Your Location"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        eventRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                //mMap.clear(); // Clear all existing markers on the map
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    for (DataSnapshot eventSnapshot : userSnapshot.child("events").getChildren()) {
                        try {
                            // Get the event details from the snapshot
                            String title = eventSnapshot.child("title").getValue(String.class);
                            String description = eventSnapshot.child("description").getValue(String.class);
                            String time = eventSnapshot.child("time").getValue(String.class);
                            double latitude = eventSnapshot.child("latitude").getValue(Double.class);
                            double longitude = eventSnapshot.child("longitude").getValue(Double.class);

                            // Add a marker to the map
                            LatLng latLng = new LatLng(latitude, longitude);
                            MarkerOptions markerOptions = new MarkerOptions()
                                    .position(latLng)
                                    .title(title)
                                    .snippet(username + "\n" +major + "\nDescription: " + description + "\nTime: " + time)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
                            Marker marker = mMap.addMarker(markerOptions);
                            marker.setTag("event markers"); // Set the marker tag to the event ID

                            mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                                @Override
                                public View getInfoWindow(Marker marker) {
                                    return null;
                                }

                                @Override
                                public View getInfoContents(Marker marker) {
                                    // Inflate the layout for the InfoWindow
                                    View view = getLayoutInflater().inflate(R.layout.custom_info_window, null);

                                    // Set the title, description, and time in the layout
                                    TextView titleTextView = view.findViewById(R.id.event_title);
                                    TextView descriptionTextView = view.findViewById(R.id.event_description);
                                    TextView timeTextView = view.findViewById(R.id.event_time);
                                    titleTextView.setText(marker.getTitle());
                                    descriptionTextView.setText(marker.getSnippet());
                                    timeTextView.setVisibility(View.GONE); // Hide the time field

                                    return view;
                                }
                            });

                            // Show the InfoWindow when the marker is clicked
                            marker.showInfoWindow();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {

                // Show a pop-up dialog with EditTexts for the user to input the event details
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                TextView titleTextView = new TextView(getActivity());
                titleTextView.setText("\n Set Event Details");

                titleTextView.setTextColor(Color.RED);
                titleTextView.setTextSize(20);
                titleTextView.setTypeface(null, Typeface.BOLD);

// Set the custom title view of the AlertDialog
                builder.setCustomTitle(titleTextView);

// Set up the input fields
                final EditText titleInput = new EditText(getActivity());
                titleInput.setHint("Title");
                titleInput.setHintTextColor(Color.RED);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                titleParams.setMargins(40, 40, 40, 0); // Set left, top, right, bottom margins
                titleInput.setLayoutParams(titleParams);

                final EditText descriptionInput = new EditText(getActivity());
                descriptionInput.setHint("Description");
                descriptionInput.setHintTextColor(Color.RED);
                LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                descriptionParams.setMargins(40, 20, 40, 0); // Set left, top, right, bottom margins
                descriptionInput.setLayoutParams(descriptionParams);

                final EditText timeInput = new EditText(getActivity());
                timeInput.setHint("Time");
                timeInput.setHintTextColor(Color.RED);
                LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                timeParams.setMargins(40, 20, 40, 40); // Set left, top, right, bottom margins
                timeInput.setLayoutParams(timeParams);

// Set up the view
                LinearLayout layout = new LinearLayout(getActivity());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.addView(titleInput);
                layout.addView(descriptionInput);
                layout.addView(timeInput);

                layout.setPadding(40, 40, 40, 40);
                layout.setBackground(ContextCompat.getDrawable(getActivity(), R.drawable.rounded_dialog_bg));

                builder.setView(layout);
                // Set up the OK button
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Save the event details and add a marker to the map

// Get the input values from the EditTexts
                        String title = titleInput.getText().toString().trim();
                        String description = descriptionInput.getText().toString().trim();
                        String time = timeInput.getText().toString().trim();
                        double latitude = latLng.latitude;
                        double longitude = latLng.longitude;

                        // Create a new event object and push it to the database
                        DatabaseReference newEventRef = eventRef.push();
                        String eventId = newEventRef.getKey();
                        Event event = new Event(eventId, title, description, time, latitude, longitude);
                        newEventRef.setValue(event);

                        // Add a marker to the map
                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(latLng)
                                .title(title)
                                .snippet(username + "\n" +major + "\nDescription: " + description + "\nTime: " + time)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
                        Marker marker = mMap.addMarker(markerOptions);
                        


                        saveMarkerToFirebase(marker);


                        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                            @Override
                            public View getInfoWindow(Marker marker) {
                                return null;
                            }

                            @Override
                            public View getInfoContents(Marker marker) {
                                // Inflate the layout for the InfoWindow
                                View view = getLayoutInflater().inflate(R.layout.custom_info_window, null);

                                // Set the title, description, and time in the layout
                                TextView titleTextView = view.findViewById(R.id.event_title);
                                TextView descriptionTextView = view.findViewById(R.id.event_description);
                                TextView timeTextView = view.findViewById(R.id.event_time);
                                titleTextView.setText(marker.getTitle());
                                descriptionTextView.setText(marker.getSnippet());
                                timeTextView.setVisibility(View.GONE); // Hide the time field

                                return view;
                            }
                        });



                        // Show the InfoWindow when the marker is clicked
                        marker.showInfoWindow();
                    }
                });

                // Set up the Cancel button
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                // Show the dialog
                builder.show();
            }
        });

        loadMarkersFromFirebase();

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                try {
                    editTextLatitude.setText(Double.toString(location.getLatitude()));
                    editTextLongitude.setText(Double.toString(location.getLongitude()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (previousLocation == null || location.distanceTo(previousLocation) > MIN_DIST) {
                    // Get the current user's ID
                    String uid = mAuth.getCurrentUser().getUid();

                    // Set up a database reference for the user's location
                    DatabaseReference userLocationReference = databaseReference.child(uid);

                    userLocationReference.child("user_latitude").setValue(location.getLatitude());
                    userLocationReference.child("user_longitude").setValue(location.getLongitude());
                    previousLocation = location;
                }
            }

            @Override
            public void onProviderDisabled(String provider) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }
        };

        LocationManager locationManager = (LocationManager) getActivity().getSystemService(LOCATION_SERVICE);

        if (getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && getActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME, MIN_DIST, locationListener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME, MIN_DIST, locationListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveMarkerToFirebase(Marker marker) {
        // Query Firebase database for markers with the same position
        Query query = databaseRef.orderByChild("latitude").equalTo(marker.getPosition().latitude);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                boolean markerExists = false;
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    double latitude = snapshot.child("latitude").getValue(Double.class);
                    double longitude = snapshot.child("longitude").getValue(Double.class);
                    String title = snapshot.child("title").getValue(String.class);
                    String snippet = snapshot.child("snippet").getValue(String.class);
                    String userId =  snapshot.child("userId").getValue(String.class);;
                    MarkerData existingMarkerData = new MarkerData(latitude, longitude, title, snippet, userId);
                    if (existingMarkerData.getLongitude() == marker.getPosition().longitude) {
                        // Marker with the same position already exists
                        markerExists = true;
                        break;
                    }
                }
                if (!markerExists) {

                    // Create a new marker entry in Firebase database
                    String key = databaseRef.push().getKey();
                    String userId = mAuth.getCurrentUser().getUid();
                    MarkerData markerData = new MarkerData(marker.getPosition().latitude,
                            marker.getPosition().longitude, marker.getTitle(), marker.getSnippet(), userId);
                    databaseRef.child(key).setValue(markerData);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to query markers from Firebase database", databaseError.toException());
            }
        });
    }

    private void loadMarkersFromFirebase() {
        // Load all marker entries from Firebase database
        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    // Get the values from the snapshot and create a MarkerData object manually
                    double latitude = snapshot.child("latitude").getValue(Double.class);
                    double longitude = snapshot.child("longitude").getValue(Double.class);
                    String title = snapshot.child("title").getValue(String.class);
                    String snippet = snapshot.child("snippet").getValue(String.class);
                    String userId =  snapshot.child("userId").getValue(String.class);;
                    MarkerData markerData = new MarkerData(latitude, longitude, title, snippet, userId);

                    LatLng latLng = new LatLng(markerData.getLatitude(), markerData.getLongitude());
                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(latLng)
                            .title(markerData.getTitle())
                            .snippet(markerData.getSnippet())
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));

                    // Check if the marker already exists in the list
                    boolean markerExists = false;
                    for (Marker existingMarker : markerList) {
                        if (existingMarker.getTag() != null && existingMarker.getTag().equals(snapshot.getKey())) {
                            markerExists = true;
                            break;
                        }
                    }

                    // Add the marker to the list and set its tag to the Firebase key
                    if (!markerExists) {
                        if (userId.equals(mAuth.getCurrentUser().getUid())) {
                            markerList.add(marker);
                        }
                        marker.setTag(snapshot.getKey());
                        Log.d("FirebaseKey", snapshot.getKey());
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Failed to load markers from Firebase database", databaseError.toException());
            }
        });

        // Set the OnMarkerClickListener for the map
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                // Set the info window adapter for the marker
                mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                    @Override
                    public View getInfoWindow(Marker marker) {
                        return null;
                    }

                    @Override
                    public View getInfoContents(Marker marker) {
                        // Inflate the layout for the InfoWindow
                        View view = getLayoutInflater().inflate(R.layout.custom_info_window, null);

                        // Set the title, description, and time in the layout
                        TextView titleTextView = view.findViewById(R.id.event_title);
                        TextView descriptionTextView = view.findViewById(R.id.event_description);
                        TextView timeTextView = view.findViewById(R.id.event_time);
                        titleTextView.setText(marker.getTitle());
                        descriptionTextView.setText(marker.getSnippet());
                        //timeTextView.setVisibility(View.GONE); // Hide the time field

                        return view;
                    }
                });

                // Show the info window for the marker
                marker.showInfoWindow();

                return true;
            }
        });
    }


    @Override
    public void onStop() {
        super.onStop();
        // Save all markers to Firebase database before app closes
        for (Marker marker : markerList) {
            saveMarkerToFirebase(marker);
        }
    }



    public class Event {
        private String eventId;
        private String title;
        private String description;
        private String time;
        private double latitude;
        private double longitude;

        public Event(String eventId, String title, String description, String time, double latitude, double longitude) {
            this.eventId = eventId;
            this.title = title;
            this.description = description;
            this.time = time;
            this.latitude = latitude;
            this.longitude = longitude;

        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }
    }

    public class MarkerData {
        private double latitude;
        private double longitude;
        private String title;
        private String snippet;

        private String userId;


        public MarkerData() {

        }

        public MarkerData(double latitude, double longitude, String title, String snippet, String userId) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.title = title;
            this.snippet = snippet;
            this.userId = userId;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }



    }

}