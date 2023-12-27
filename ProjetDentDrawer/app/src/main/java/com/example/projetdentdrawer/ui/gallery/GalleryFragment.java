package com.example.projetdentdrawer.ui.gallery;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.projetdentdrawer.R;
import com.google.android.material.navigation.NavigationView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GalleryFragment extends Fragment implements View.OnClickListener{

    private static final int PICK_IMAGE_REQUEST = 1;
    private ImageView processedImageView;
    private List<Point> selectedPoints = new ArrayList<>();
    private List<Point> edgePoints = new ArrayList<>();

    Mat originalImage;


    Button saveButton;
    private Handler handler = new Handler();
    private int selectionCounter = 0;


    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gallery, container, false);

        processedImageView = view.findViewById(R.id.processedImageView);
        view.findViewById(R.id.selectImageButton).setOnClickListener(this);
        saveButton = view.findViewById(R.id.saveImageButton);

        processedImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    handleTouch(event.getX(), event.getY());
                }
                return true;
            }
        });
        Button resetButton = view.findViewById(R.id.button2);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetState();

            }
        });

        if (OpenCVLoader.initDebug()) {
            showToast("OpenCV loaded successfully");
        } else {
            showToast("OpenCV initialization failed!");
        }
// Définissez l'attribut android:onClick pour le bouton "Enregistrer"
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSaveButtonClick(v);
            }
        });
        // Réinitialiser le compteur et masquer le bouton "Enregistrer"
        selectionCounter = 0;
        saveButton.setVisibility(View.GONE);
        return view;
    }
    private void resetState() {
        clearSelectedPoints();
        edgePoints.clear();
        selectionCounter = 0;
        saveButton.setVisibility(View.GONE);
        processedImageView.setImageBitmap(null);
        updateTextView(R.id.leftAngleTextView, "Left Taper Angle: ");
        updateTextView(R.id.rightAngleTextView, "Right Taper Angle: ");
    }


    private void handleTouch(float x, float y) {
        // Convert touch coordinates to image coordinates
        if (selectedPoints.size() == 4) {
            calculateAndDisplayTaperAngles(selectedPoints);
            return;
        }
        // Vérifiez si la limite de sélection est atteinte
        if (selectionCounter >= 3) {
            showToastAndHide("La sélection est désactivée.", 2000); // Affiche le message pendant 2000 millisecondes (2 secondes)
            return;
        }


        Point imagePoint = touchToImageCoordinates(x, y);

        // Find the closest edge point to the touched point
        Point closestEdgePoint = findClosestEdgePoint(imagePoint);

        // Check if the closest point is already selected
        if (!selectedPoints.contains(closestEdgePoint)) {
            selectedPoints.add(closestEdgePoint);

            // Convert grayscale image to BGR color image
            Mat colorImage = new Mat();
            Imgproc.cvtColor(originalImage, colorImage, Imgproc.COLOR_GRAY2BGR);

            // Draw circles for all selected points on the color image
            for (Point selectedPoint : selectedPoints) {
                Imgproc.circle(colorImage, selectedPoint, 5, new Scalar(255, 0, 0), -1);
            }

            // Display the updated color image
            displayImage(colorImage);

            if (selectedPoints.size() == 4) {
                calculateAndDisplayTaperAngles(selectedPoints);

                // Réinitialiser les points après chaque troisième sélection
                clearSelectedPoints();
                selectionCounter++;

                // Afficher le compteur (vous pouvez le mettre à jour comme vous le souhaitez)
                showToast("Sélection " + selectionCounter + " effectuée");


            }
        }
    }
    private void showToastAndHide(String message, int duration) {
        Toast toast = Toast.makeText(requireActivity(), message, Toast.LENGTH_SHORT);
        toast.show();

        // Utilisez Handler pour retirer le message après la durée spécifiée
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                toast.cancel(); // Retire le message
            }
        }, duration);
    }


    private void clearSelectedPoints() {
        selectedPoints.clear();
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == getActivity().RESULT_OK && data != null) {
            // Reset the state for a new image
            clearSelectedPoints();

            Uri selectedImageUri = data.getData();

            processImage(selectedImageUri);
        }
    }



    private Point findClosestEdgePoint(Point touchPoint) {
        // Initialize with a large distance
        double minDistance = Double.MAX_VALUE;
        Point closestPoint = null;

        // Iterate through the stored edge points to find the closest one
        for (Point edgePoint : edgePoints) {
            double distance = Math.sqrt(Math.pow(touchPoint.x - edgePoint.x, 2) + Math.pow(touchPoint.y - edgePoint.y, 2));
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = edgePoint;
            }
        }

        return closestPoint;
    }

    private Point touchToImageCoordinates(float x, float y) {
        // Get the width and height of the displayed image view
        int viewWidth = processedImageView.getWidth();
        int viewHeight = processedImageView.getHeight();

        // Get the dimensions of the original image
        int imageWidth = originalImage.cols();
        int imageHeight = originalImage.rows();

        // Calculate the scaling factors for width and height
        double xScale = imageWidth / (double) viewWidth;
        double yScale = imageHeight / (double) viewHeight;

        // Calculate the image coordinates
        double imageX = x * xScale;
        double imageY = y * yScale;

        return new Point(imageX, imageY);
    }



    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.selectImageButton) {
            openImagePicker();
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }


    private void processImage(Uri imageUri) {
        try {
            InputStream inputStream = requireActivity().getContentResolver().openInputStream(imageUri);

            byte[] imageBytes = readInputStream(inputStream);

            originalImage = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_UNCHANGED);

            Imgproc.cvtColor(originalImage, originalImage, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(originalImage, originalImage, new Size(9, 9), 0);
            Imgproc.Canny(originalImage, originalImage, 50, 150);
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
            Imgproc.dilate(originalImage, originalImage, kernel);

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(originalImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            for (MatOfPoint contour : contours) {
                for (Point point : contour.toArray()) {
                    edgePoints.add(point);
                }
            }

            // Hough Transform for Line Detection
            Mat lines = new Mat();
            Imgproc.HoughLinesP(originalImage, lines, 1, Math.PI / 180, 50, 50, 10);


            displayImage(originalImage);
            // Calculate and Display Taper Angles directly after detecting edge points
            calculateAndDisplayTaperAngles(selectedPoints);

            // Calculate and Store Angles
            List<Double> leftTaperAngles = new ArrayList<>();
            List<Double> rightTaperAngles = new ArrayList<>();
            for (int i = 0; i < lines.rows(); i++) {
                double[] line = lines.get(i, 0);
                double angleRadians = Math.atan2(line[3] - line[1], line[2] - line[0]);
                double angleDegrees = Math.toDegrees(angleRadians);

                // Store the angle values for later evaluation
                if (angleDegrees < 0) {
                    leftTaperAngles.add(angleDegrees);
                } else {
                    rightTaperAngles.add(angleDegrees);
                }
            }

            // Display Angles on UI
            //displayAnglesOnUI(leftTaperAngles, rightTaperAngles);

            // ... (Additional code for angle comparison, user interface, and result reporting)
        } catch (IOException e) {
            e.printStackTrace();
            showToast("Error processing image");
        }


    }

    private void calculateAndDisplayTaperAngles(List<Point> selectedPoints) {
        // Ensure exactly 4 points are selected
        if (selectedPoints.size() != 4) {
            showToast("Please select 4 points");
            return;
        }

        // Order the points based on x-coordinate (assuming left to right order)
        Collections.sort(selectedPoints, new Comparator<Point>() {
            @Override
            public int compare(Point p1, Point p2) {
                return Double.compare(p1.x, p2.x);
            }
        });

        // Separate the points into left and right sides
        Point leftTop = selectedPoints.get(0);
        Point leftBottom = selectedPoints.get(1);
        Point rightTop = selectedPoints.get(2);
        Point rightBottom = selectedPoints.get(3);

        // Calculate distances for taper angle
        double deltaY = Math.abs(leftTop.y - leftBottom.y);
        double deltaX = Math.abs(leftTop.x - leftBottom.x);
        double L = Math.hypot(deltaY, deltaX);

        // Calculate the taper angle relative to horizontal in degrees
        double taperAngleRad = Math.atan(deltaY / deltaX);
        double taperAngleDeg = Math.toDegrees(taperAngleRad);

        // Calculate the angle on the other side relative to vertical in degrees
        double deltaY2 = Math.abs(rightTop.y - rightBottom.y);
        double taperAngleRad2 = Math.atan(deltaY2 / deltaX);
        double taperAngleDeg2 = Math.toDegrees(taperAngleRad2);

        Mat colorImage = new Mat();
        Imgproc.cvtColor(originalImage, colorImage, Imgproc.COLOR_GRAY2BGR);

        // Draw left tangent line
        Imgproc.line(colorImage, leftTop, leftBottom, new Scalar(0, 255, 0), 2);

        // Draw right tangent line
        Imgproc.line(colorImage, rightTop, rightBottom, new Scalar(0, 255, 0), 2);

        // Display the updated color image
        displayImage(colorImage);

        // Draw vertical lines for the left side
        drawLines(leftTop, leftBottom, taperAngleDeg);

        // Draw vertical lines for the right side
        drawLines(rightBottom, rightTop, taperAngleDeg2);

        // Display the results (you can update TextViews or any UI elements here)
        displayAnglesOnUI(taperAngleDeg, taperAngleDeg2);
        // Rendre le bouton "Enregistrer" visible après l'affichage complet
        handler.post(new Runnable() {
            @Override
            public void run() {
                saveButton.setVisibility(View.VISIBLE);
            }
        });

    }
    private void drawLines(Point top, Point bottom, double taperAngle) {
        if (selectionCounter >= 0) {
            Imgproc.line(originalImage, new Point(top.x, 0), new Point(top.x, top.y), new Scalar(255, 0, 0), 2);
        }

    }

    private void displayAnglesOnUI(double leftTaperAngle, double rightTaperAngle) {
// Subtract angles from 90 degrees
        double displayLeftTaperAngle = 90 - leftTaperAngle;
        double displayRightTaperAngle = 90 - rightTaperAngle;

        // Récupérez les TextViews
        TextView leftAngleTextView = getView().findViewById(R.id.leftAngleTextView);
        TextView rightAngleTextView = getView().findViewById(R.id.rightAngleTextView);

        // Ajoutez les angles à chaque sélection en dessous l'un de l'autre
        leftAngleTextView.append(String.format("%.2f", displayLeftTaperAngle) + "\n");
        rightAngleTextView.append(String.format("%.2f", displayRightTaperAngle) + "\n");
    }





    private void updateTextView(int textViewId, String text) {
        TextView textView = getView().findViewById(textViewId);

        textView.setText(text);
    }

    private void displayImage(Mat image) {
        Bitmap processedBitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, processedBitmap);
        processedImageView.setImageBitmap(processedBitmap);
    }


    private byte[] readInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        return outputStream.toByteArray();
    }

    private void showToast(String message) {
        Toast.makeText(requireActivity(), message, Toast.LENGTH_SHORT).show();
    }


    private void saveImageToDatabase(Mat image) {
        Bitmap bitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, bitmap);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        String encodedImage = Base64.encodeToString(imageBytes, Base64.DEFAULT);

        //  l'URL de votre script PHP côté serveur
        String serverUrl = "http://128.10.0.237/back_dents/save2.php";

        RequestQueue queue = Volley.newRequestQueue(requireActivity());
        StringRequest request = new StringRequest(Request.Method.POST, serverUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Gérez la réponse du serveur si nécessaire
                        showToast(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Gérez les erreurs de requête si nécessaire
                        showToast("Erreur de requête: " + error.getMessage());

                    }
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("image", encodedImage);
                return params;
            }
        };

        queue.add(request);
    }

// ...
    // Ajoutez cette méthode à votre bouton "Enregistrer" onClick
    public void onSaveButtonClick(View view) {
        saveImageToDatabase(originalImage);
    }


}