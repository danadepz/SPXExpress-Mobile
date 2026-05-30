package com.spx.express.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.spx.express.R
import com.spx.express.data.api.RetrofitClient
import com.spx.express.data.model.Customer
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    private lateinit var etFname: TextInputEditText
    private lateinit var etLname: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnSignUp: Button
    private lateinit var tvLoginLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize views
        etFname = findViewById(R.id.etFname)
        etLname = findViewById(R.id.etLname)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSignUp = findViewById(R.id.btnSignUp)
        tvLoginLink = findViewById(R.id.tvLoginLink)

        btnSignUp.setOnClickListener {
            handleSignUp()
        }

        tvLoginLink.setOnClickListener {
            // Go back to login screen
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun handleSignUp() {
        val fname = etFname.text?.toString()?.trim() ?: ""
        val lname = etLname.text?.toString()?.trim() ?: ""
        val email = etEmail.text?.toString()?.trim() ?: ""
        val phone = etPhone.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString()?.trim() ?: ""
        val confirmPassword = etConfirmPassword.text?.toString()?.trim() ?: ""

        // Validate first name
        if (fname.isEmpty() || !fname.matches(Regex("^[a-zA-Z\\s]+$"))) {
            etFname.error = "First name must contain letters and spaces only"
            etFname.requestFocus()
            return
        }

        // Validate last name
        if (lname.isEmpty() || !lname.matches(Regex("^[a-zA-Z\\s]+$"))) {
            etLname.error = "Last name must contain letters and spaces only"
            etLname.requestFocus()
            return
        }

        // Validate email
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches() || !email.lowercase().endsWith(".com")) {
            etEmail.error = "Please enter a valid email address ending with .com"
            etEmail.requestFocus()
            return
        }

        // Validate phone number
        if (phone.isEmpty() || !phone.matches(Regex("^[0-9]{11}$"))) {
            etPhone.error = "Phone number must be exactly 11 digits"
            etPhone.requestFocus()
            return
        }

        // Validate password
        if (password.length < 6) {
            etPassword.error = "Password must be at least 6 characters"
            etPassword.requestFocus()
            return
        }

        // Validate password match
        if (password != confirmPassword) {
            etConfirmPassword.error = "Passwords do not match"
            etConfirmPassword.requestFocus()
            return
        }

        btnSignUp.isEnabled = false
        btnSignUp.text = "Creating Account..."

        lifecycleScope.launch {
            try {
                // 1. Fetch all customers to check duplicate email and find next ID
                val allCustomersResponse = RetrofitClient.instance.getAllCustomers()
                if (allCustomersResponse.isSuccessful) {
                    val customersMap = allCustomersResponse.body() ?: emptyMap()
                    
                    // Check if email already exists
                    val emailExists = customersMap.values.any { it.custEmail.equals(email, ignoreCase = true) }
                    if (emailExists) {
                        etEmail.error = "An account with this email already exists"
                        etEmail.requestFocus()
                        btnSignUp.isEnabled = true
                        btnSignUp.text = "Sign Up"
                        return@launch
                    }

                    // Find max customer ID to sequence next ID
                    val maxId = customersMap.values.map { it.custId }.maxOrNull() ?: 0
                    val nextCustId = maxId + 1

                    // Create hash of password for web portal sync compatibility
                    val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val createdAtStr = dateFormat.format(Date())

                    val newCustomer = Customer(
                        custId = nextCustId,
                        custEmail = email,
                        custPasswordHash = hashedPassword,
                        custFname = fname,
                        custLname = lname,
                        custContactNumber = phone,
                        custStreet = null,
                        custCity = null,
                        custProvince = null,
                        custPostalCode = null,
                        custIsActive = 1,
                        custCreatedAt = createdAtStr
                    )

                    // 2. Write to Firebase under `/customer/{nextCustId}`
                    val registerResponse = RetrofitClient.instance.addCustomer(nextCustId, newCustomer)
                    if (registerResponse.isSuccessful) {
                        Toast.makeText(this@RegisterActivity, "Account created successfully! Please sign in.", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@RegisterActivity, "Failed to create account. Please try again.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@RegisterActivity, "Failed to connect. Please try again.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Network Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                btnSignUp.isEnabled = true
                btnSignUp.text = "Sign Up"
            }
        }
    }
}
